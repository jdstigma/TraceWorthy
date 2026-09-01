"""
iphone_gui.py — desktop control panel for turning an iPhone's call history into a
TraceWorthy evidence packet.

Workflow:
  Backup tab   → pick a local iTunes / Apple Devices / Finder backup, extract the
                 call history to iphone_calls.csv (asks for the backup password
                 only if the backup is encrypted).
  My info tab  → enter your details once; saved to traceworthy_profile.json.
  Generate tab → build the full evidence packet (FCC complaint, police cover note,
                 carrier script, incident timeline, evidence summary + bundle).

Standard-library GUI (tkinter). Everything runs locally; nothing is uploaded.
Bundles to one .exe with build_exe.bat.
"""

from __future__ import annotations

import os
import subprocess
import sys
import threading
import tkinter as tk
from tkinter import filedialog, messagebox, ttk

if getattr(sys, "frozen", False):
    BASE = os.path.dirname(sys.executable)
else:
    BASE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, BASE)
sys.path.insert(0, os.path.join(BASE, os.pardir, "analysis"))

os.environ.setdefault("MPLBACKEND", "Agg")

from profile import HarassmentType, Profile  # noqa: E402
import backup_locator as bl  # noqa: E402
import callhistory_parser as chp  # noqa: E402
from callhistory_parser import IPHONE_SOURCE_NOTE  # noqa: E402


def _workdir() -> str:
    """A stable, findable place for outputs — the exe's cwd is unpredictable when
    it's double-clicked (Windows may run it from a scoped temp dir)."""
    for cand in (os.path.join(os.path.expanduser("~"), "Documents", "TraceWorthy"),
                 os.path.join(os.path.expanduser("~"), "TraceWorthy")):
        try:
            os.makedirs(cand, exist_ok=True)
            return cand
        except OSError:
            continue
    return os.getcwd()


WORKDIR = _workdir()
CSV_PATH = os.path.join(WORKDIR, "iphone_calls.csv")
OUT_DIR = os.path.join(WORKDIR, "iphone_packet")
PROFILE_PATH = os.path.join(WORKDIR, "traceworthy_profile.json")

STATES = ["", "AL", "AK", "AZ", "AR", "CA", "CO", "CT", "DE", "DC", "FL", "GA", "HI", "ID", "IL",
          "IN", "IA", "KS", "KY", "LA", "ME", "MD", "MA", "MI", "MN", "MS", "MO", "MT", "NE", "NV",
          "NH", "NJ", "NM", "NY", "NC", "ND", "OH", "OK", "OR", "PA", "RI", "SC", "SD", "TN", "TX",
          "UT", "VT", "VA", "WA", "WV", "WI", "WY"]
CARRIERS = ["", "AT&T", "Verizon", "T-Mobile", "US Cellular", "Cricket", "Metro by T-Mobile",
            "Boost Mobile", "Google Fi", "Mint Mobile", "Straight Talk", "Visible", "Xfinity Mobile"]


class App(tk.Tk):
    def __init__(self):
        super().__init__()
        self.title("TraceWorthy — iPhone")
        self.geometry("820x680")

        self.backups: list[bl.Backup] = []
        self.vars: dict[str, tk.StringVar] = {}
        self._notes_rows: list[dict] = []
        self._notes_header: list[str] = []
        self._notes_sel: str | None = None
        self._safe_numbers: list[str] = []

        nb = ttk.Notebook(self)
        nb.pack(fill="both", expand=True, padx=8, pady=(8, 4))
        self.backup_tab = ttk.Frame(nb)
        self.notes_tab = ttk.Frame(nb)
        self.wl_tab = ttk.Frame(nb)
        self.info_tab = ttk.Frame(nb)
        self.gen_tab = ttk.Frame(nb)
        nb.add(self.backup_tab, text="1 · Backup")
        nb.add(self.notes_tab, text="2 · Notes")
        nb.add(self.wl_tab, text="3 · White list")
        nb.add(self.info_tab, text="4 · My info")
        nb.add(self.gen_tab, text="5 · Generate")
        nb.bind("<<NotebookTabChanged>>", self._on_tab_changed)
        self._nb = nb

        self._build_backup_tab()
        self._build_notes_tab()
        self._build_wl_tab()
        self._build_info_tab()
        self._build_gen_tab()

        self.log = tk.Text(self, height=11, wrap="word", bg="#111", fg="#eee",
                           insertbackground="#eee")
        self.log.pack(fill="both", expand=False, padx=10, pady=(0, 10))
        self._log("Ready. Step 1: make an ENCRYPTED local backup of the iPhone, then click "
                  "Refresh.\n")
        self._log(f"Output folder: {WORKDIR}\n")

        self.refresh_backups()
        self.load_profile(initial=True)

    # ------------------------------------------------------------- logging --
    def _log(self, text):
        self.log.insert("end", text)
        self.log.see("end")

    def _run(self, fn, label):
        class _W:
            def __init__(s, app): s.app = app
            def write(s, x):
                if x:
                    s.app.after(0, s.app._log, x)
            def flush(s): pass

        def worker():
            self.after(0, self._log, f"\n> {label}\n")
            out, err = sys.stdout, sys.stderr
            sys.stdout = sys.stderr = _W(self)
            try:
                fn()
                self.after(0, self._log, "Done.\n")
            except Exception as e:  # noqa: BLE001
                self.after(0, self._log, f"ERROR: {e}\n")
            finally:
                sys.stdout, sys.stderr = out, err
        threading.Thread(target=worker, daemon=True).start()

    # -------------------------------------------------------------- Backup --
    def _build_backup_tab(self):
        pad = {"padx": 10, "pady": 5}
        f = self.backup_tab
        ttk.Label(f, text="Pick the iPhone backup to read call history from:",
                  font=("Segoe UI", 10, "bold")).pack(anchor="w", **pad)

        row = ttk.Frame(f)
        row.pack(fill="x", **pad)
        self.backup_combo = ttk.Combobox(row, state="readonly", width=70)
        self.backup_combo.pack(side="left", fill="x", expand=True)
        self.backup_combo.bind("<<ComboboxSelected>>", lambda _e: self._on_backup_pick())
        ttk.Button(row, text="Refresh", command=self.refresh_backups).pack(side="left", padx=4)
        ttk.Button(row, text="Browse…", command=self._browse_backup).pack(side="left")

        pw = ttk.Frame(f)
        pw.pack(fill="x", **pad)
        ttk.Label(pw, text="Backup password:").pack(side="left")
        self.pw_var = tk.StringVar()
        self.pw_entry = ttk.Entry(pw, textvariable=self.pw_var, show="•", width=32)
        self.pw_entry.pack(side="left", padx=6)
        self.pw_hint = ttk.Label(pw, text="", foreground="#666")
        self.pw_hint.pack(side="left")

        self.facetime_var = tk.BooleanVar(value=False)
        ttk.Checkbutton(f, text="Also include FaceTime calls (default: cellular calls only)",
                        variable=self.facetime_var).pack(anchor="w", **pad)
        self.appcalls_var = tk.BooleanVar(value=False)
        ttk.Checkbutton(f, text="Also include third-party app calls (WhatsApp, etc.)",
                        variable=self.appcalls_var).pack(anchor="w", **pad)
        self.outgoing_var = tk.BooleanVar(value=False)
        ttk.Checkbutton(f, text="Also include calls you placed (default: incoming only)",
                        variable=self.outgoing_var).pack(anchor="w", **pad)

        ttk.Button(f, text="Extract call history  ➜  iphone_calls.csv",
                   command=self.extract).pack(fill="x", **pad)
        ttk.Button(f, text="Inspect backup  (diagnose \"No calls found\")",
                   command=self.inspect_backup).pack(fill="x", **pad)
        ttk.Label(f, text=IPHONE_SOURCE_NOTE, wraplength=760, foreground="#666").pack(anchor="w", **pad)

    def _on_backup_pick(self):
        b = self._selected_backup()
        if b and b.encrypted:
            self.pw_entry.configure(state="normal")
            self.pw_hint.configure(text="(encrypted — enter the backup password)")
        else:
            self.pw_var.set("")
            self.pw_entry.configure(state="disabled")
            self.pw_hint.configure(text="(not encrypted)" if b else "")
        if b and not b.encrypted and bl.backup_has_call_history(b) is False:
            self._log(
                "\nHEADS UP: this backup has no call history. iOS only saves call history "
                "in ENCRYPTED backups. In the Apple Devices app / iTunes / Finder, tick "
                '"Encrypt local backup", set a password, back up again, then Refresh.\n'
            )

    def _selected_backup(self) -> bl.Backup | None:
        i = self.backup_combo.current()
        return self.backups[i] if 0 <= i < len(self.backups) else None

    def refresh_backups(self):
        try:
            self.backups = bl.find_backups()
        except Exception as e:  # noqa: BLE001
            self._log(f"Could not scan for backups: {e}\n")
            self.backups = []
        self.backup_combo["values"] = [b.label() for b in self.backups]
        if self.backups:
            self.backup_combo.current(0)
            self._on_backup_pick()
            self._log(f"Found {len(self.backups)} backup(s).\n")
        else:
            self.backup_combo.set("")
            self._log("No local backups found. Make one, then click Refresh — or use Browse…\n")

    def _browse_backup(self):
        folder = filedialog.askdirectory(title="Select an iPhone backup folder (contains Manifest.plist)")
        if not folder:
            return
        if not os.path.isfile(os.path.join(folder, "Manifest.plist")):
            messagebox.showerror("Not a backup", "That folder has no Manifest.plist — it is not an iPhone backup.")
            return
        b = bl.describe_backup(folder)
        self.backups.insert(0, b)
        self.backup_combo["values"] = [x.label() for x in self.backups]
        self.backup_combo.current(0)
        self._on_backup_pick()

    def extract(self):
        b = self._selected_backup()
        if not b:
            messagebox.showwarning("No backup", "Pick a backup first (Refresh, or Browse…).")
            return
        pw = self.pw_var.get() if b.encrypted else None
        facetime = self.facetime_var.get()
        appcalls = self.appcalls_var.get()
        outgoing = self.outgoing_var.get()

        def work():
            import tempfile
            workdir = tempfile.mkdtemp(prefix="tw_iphone_")
            storedata = bl.extract_call_history(b, os.path.join(workdir, "ch.storedata"), pw)
            ab = bl.extract_address_book(b, os.path.join(workdir, "ab.sqlitedb"), pw)
            try:
                calls = chp.parse(storedata, ab, include_facetime=facetime,
                                  include_app_calls=appcalls, include_outgoing=outgoing)
            except chp.NoCallRecords as e:
                print(str(e))
                print("\nRun 'Inspect backup' (below) for a full breakdown of what the DB contains.")
                return
            chp.write_csv(calls, CSV_PATH)
            flagged = sum(1 for c in calls if c.suspicious())
            print(f"{len(calls)} calls ({flagged} flagged) written to {CSV_PATH}")

        self._run(work, f"Extracting call history from: {b.label()}")

    def inspect_backup(self):
        b = self._selected_backup()
        if not b:
            messagebox.showwarning("No backup", "Pick a backup first.")
            return
        pw = self.pw_var.get() if b.encrypted else None

        def work():
            import contextlib
            import io
            import tempfile
            from types import SimpleNamespace
            from cli import cmd_inspect
            workdir = tempfile.mkdtemp(prefix="tw_iphone_")
            storedata = bl.extract_call_history(b, os.path.join(workdir, "ch.storedata"), pw)
            buf = io.StringIO()
            with contextlib.redirect_stdout(buf):
                cmd_inspect(SimpleNamespace(storedata=storedata, backup=None, password=None))
            print(buf.getvalue())

        self._run(work, f"Inspecting: {b.label()}")

    # --------------------------------------------------------------- Notes --
    def _build_notes_tab(self):
        f = self.notes_tab
        top = ttk.Frame(f)
        top.pack(fill="x", padx=10, pady=(8, 4))
        ttk.Label(top, text="Add a note + severity to a call. These feed the incident timeline.",
                  font=("Segoe UI", 10, "bold")).pack(side="left")
        self.flagged_only = tk.BooleanVar(value=True)
        ttk.Checkbutton(top, text="Flagged calls only", variable=self.flagged_only,
                        command=self._reload_notes_tree).pack(side="right")
        ttk.Button(top, text="Reload CSV", command=self._load_notes_csv).pack(side="right", padx=6)

        cols = ("when", "number", "type", "sev", "note")
        self.tree = ttk.Treeview(f, columns=cols, show="headings", height=12)
        for c, w in zip(cols, (130, 130, 70, 90, 300)):
            self.tree.heading(c, text=c.title())
            self.tree.column(c, width=w, anchor="w")
        self.tree.pack(fill="both", expand=True, padx=10, pady=4)
        self.tree.bind("<<TreeviewSelect>>", self._on_note_select)

        ed = ttk.Frame(f)
        ed.pack(fill="x", padx=10, pady=6)
        ttk.Label(ed, text="Severity:").grid(row=0, column=0, sticky="w")
        self.sev_var = tk.StringVar(value="")
        ttk.Combobox(ed, textvariable=self.sev_var, width=14, state="readonly",
                     values=["", "Silent", "Spoken", "Threatening"]).grid(row=0, column=1, sticky="w", padx=6)
        ttk.Label(ed, text="Note:").grid(row=1, column=0, sticky="nw", pady=(6, 0))
        self.note_text = tk.Text(ed, height=3, width=70, wrap="word")
        self.note_text.grid(row=1, column=1, sticky="w", padx=6, pady=(6, 0))
        btns = ttk.Frame(ed)
        btns.grid(row=2, column=1, sticky="w", padx=6, pady=6)
        ttk.Button(btns, text="Apply to selected call", command=self._apply_note).pack(side="left")
        ttk.Button(btns, text="Save all to CSV", command=self._save_notes_csv).pack(side="left", padx=6)

    def _on_tab_changed(self, _e):
        idx = self._nb.index(self._nb.select())
        if idx == 1 and hasattr(self, "tree") and not self._notes_rows:
            self._load_notes_csv()
        elif idx == 2 and hasattr(self, "wl_tree"):
            if not self._notes_rows:
                self._load_notes_csv()
            self._wl_refresh()

    def _load_notes_csv(self):
        import csv
        if not os.path.isfile(CSV_PATH):
            self._log("No iphone_calls.csv yet — run Backup → Extract first.\n")
            return
        with open(CSV_PATH, newline="", encoding="utf-8") as fh:
            r = csv.DictReader(fh)
            self._notes_header = list(r.fieldnames or [])
            self._notes_rows = list(r)
        self._reload_notes_tree()
        self._log(f"Loaded {len(self._notes_rows)} calls into the Notes tab.\n")

    def _reload_notes_tree(self):
        self.tree.delete(*self.tree.get_children())
        for i, row in enumerate(self._notes_rows):
            if self.flagged_only.get() and (row.get("Suspicious", "").upper() != "YES"):
                continue
            self.tree.insert("", "end", iid=str(i), values=(
                row.get("Timestamp", ""), row.get("Number", ""), row.get("Type", ""),
                row.get("Severity", ""), row.get("Note", "")))

    def _on_note_select(self, _e):
        sel = self.tree.selection()
        self._notes_sel = sel[0] if sel else None
        if self._notes_sel is None:
            return
        row = self._notes_rows[int(self._notes_sel)]
        self.sev_var.set(row.get("Severity", ""))
        self.note_text.delete("1.0", "end")
        self.note_text.insert("1.0", row.get("Note", ""))

    def _apply_note(self):
        if self._notes_sel is None:
            messagebox.showinfo("Pick a call", "Select a call in the list first.")
            return
        row = self._notes_rows[int(self._notes_sel)]
        row["Severity"] = self.sev_var.get()
        row["Note"] = self.note_text.get("1.0", "end").strip()
        self.tree.item(self._notes_sel, values=(
            row.get("Timestamp", ""), row.get("Number", ""), row.get("Type", ""),
            row["Severity"], row["Note"]))
        self._log(f"Note set on {row.get('Number','')} @ {row.get('Timestamp','')}\n")

    def _save_notes_csv(self):
        import csv
        if not self._notes_rows:
            return
        with open(CSV_PATH, "w", newline="", encoding="utf-8") as fh:
            w = csv.DictWriter(fh, fieldnames=self._notes_header)
            w.writeheader()
            w.writerows(self._notes_rows)
        n = sum(1 for r in self._notes_rows if r.get("Note") or r.get("Severity"))
        self._log(f"Saved {os.path.abspath(CSV_PATH)} — {n} call(s) now have a note/severity.\n")

    # ----------------------------------------------------------- White list --
    def _build_wl_tab(self):
        f = self.wl_tab
        ttk.Label(
            f,
            text="Numbers that called you, most calls first. Tick the ones you actually know — a "
                 "friend or relative on a number you never saved. Their calls are then left out of "
                 "every figure, chart, list, and the CSV, and the evidence summary shows an "
                 "all-incoming vs. potential-harassment comparison.",
            wraplength=780, foreground="#555", justify="left",
        ).pack(anchor="w", padx=14, pady=(10, 6))

        cols = ("number", "calls", "hits", "known")
        self.wl_tree = ttk.Treeview(f, columns=cols, show="headings", height=15, selectmode="browse")
        for c, txt, w in (("number", "Number", 220), ("calls", "Calls", 70),
                          ("hits", "Pattern hits", 110), ("known", "Known caller", 110)):
            self.wl_tree.heading(c, text=txt)
            self.wl_tree.column(c, width=w, anchor="center" if c != "number" else "w")
        self.wl_tree.pack(fill="both", expand=True, padx=14)
        self.wl_tree.bind("<Double-1>", self._wl_toggle_selected)
        self.wl_tree.bind("<space>", self._wl_toggle_selected)

        bar = ttk.Frame(f)
        bar.pack(fill="x", padx=14, pady=8)
        ttk.Button(bar, text="Reload from calls", command=self._wl_reload).pack(side="left")
        ttk.Button(bar, text="Toggle selected", command=self._wl_toggle_selected).pack(side="left", padx=6)
        ttk.Button(bar, text="Save white list", command=self._wl_save).pack(side="left", padx=6)
        self.wl_manual = tk.StringVar()
        ttk.Entry(bar, textvariable=self.wl_manual, width=20).pack(side="left", padx=(18, 4))
        ttk.Button(bar, text="Add number", command=self._wl_add_manual).pack(side="left")

    def _wl_key(self, number: str) -> str:
        return Profile._num_key(number)

    def _wl_is_safe(self, number: str) -> bool:
        keys = {self._wl_key(n) for n in self._safe_numbers}
        return self._wl_key(number) in keys

    def _wl_reload(self):
        self._load_notes_csv()
        self._wl_refresh()

    def _wl_refresh(self):
        if not hasattr(self, "wl_tree"):
            return
        self.wl_tree.delete(*self.wl_tree.get_children())
        agg: dict[str, dict] = {}
        for row in self._notes_rows:
            num = (row.get("Number") or "").strip()
            if not num:
                continue
            a = agg.setdefault(num, {"calls": 0, "hits": 0})
            a["calls"] += 1
            if (row.get("Suspicious") or "").upper() == "YES":
                a["hits"] += 1
        shown_keys = set()
        for num, a in sorted(agg.items(), key=lambda kv: kv[1]["calls"], reverse=True):
            if a["calls"] < 2:
                continue
            safe = self._wl_is_safe(num)
            shown_keys.add(self._wl_key(num))
            self.wl_tree.insert("", "end", iid=num, values=(
                num, a["calls"], a["hits"] or "", "yes" if safe else ""))
        # manually-added safe numbers that didn't appear in the call log
        for n in self._safe_numbers:
            if self._wl_key(n) not in shown_keys:
                self.wl_tree.insert("", "end", iid=n, values=(n, "—", "", "yes"))
        marked = sum(1 for n in self._safe_numbers if n.strip())
        self._log(f"White list: {marked} number(s) marked as known callers.\n")

    def _wl_toggle_selected(self, _e=None):
        sel = self.wl_tree.selection()
        if not sel:
            return
        number = sel[0]
        if self._wl_is_safe(number):
            k = self._wl_key(number)
            self._safe_numbers = [n for n in self._safe_numbers if self._wl_key(n) != k]
        else:
            self._safe_numbers.append(number)
        self._wl_refresh()
        try:
            self.wl_tree.selection_set(number)
        except tk.TclError:
            pass

    def _wl_add_manual(self):
        n = self.wl_manual.get().strip()
        if n and not self._wl_is_safe(n):
            self._safe_numbers.append(n)
            self.wl_manual.set("")
            self._wl_refresh()

    def _wl_save(self):
        self.save_profile()

    # ------------------------------------------------------------- My info --
    def _build_info_tab(self):
        f = self.info_tab
        grid = ttk.Frame(f)
        grid.pack(fill="x", padx=14, pady=10)

        def field(label, key, r, combo=None, hint=None):
            ttk.Label(grid, text=label).grid(row=r, column=0, sticky="w", pady=4)
            var = tk.StringVar()
            self.vars[key] = var
            if combo is not None:
                w = ttk.Combobox(grid, textvariable=var, values=combo, width=38)
            else:
                w = ttk.Entry(grid, textvariable=var, width=40)
            w.grid(row=r, column=1, sticky="w", padx=8)
            if hint:
                ttk.Label(grid, text=hint, foreground="#666").grid(row=r, column=2, sticky="w")

        field("Full name", "full_name", 0)
        field("Contact phone", "phone", 1, hint="where police / FCC / carrier reach you")
        field("Affected number", "affected_number", 2,
              hint="the line getting the harassing calls (blank = same as contact phone)")
        field("Email", "email", 3)
        field("City", "address_city", 4)
        field("State", "state", 5, combo=STATES)
        field("Carrier", "carrier", 6, combo=CARRIERS)
        field("FCC complaint #", "fcc_complaint_number", 7)
        field("Police case #", "police_case_number", 8)
        field("Carrier case #", "carrier_case_number", 9)

        ttk.Label(grid, text="Harassment type").grid(row=10, column=0, sticky="w", pady=6)
        self.ht_var = tk.StringVar(value=HarassmentType.SILENT.value)
        htf = ttk.Frame(grid)
        htf.grid(row=10, column=1, sticky="w", padx=8)
        for ht in HarassmentType:
            ttk.Radiobutton(htf, text=ht.label, value=ht.value, variable=self.ht_var).pack(anchor="w")

        btns = ttk.Frame(f)
        btns.pack(fill="x", padx=14, pady=6)
        ttk.Button(btns, text="Save", command=self.save_profile).pack(side="left")
        ttk.Button(btns, text="Reload", command=lambda: self.load_profile()).pack(side="left", padx=6)
        ttk.Label(f, text=f"Stored locally in {os.path.abspath(PROFILE_PATH)} — blank fields show as "
                          "[PLACEHOLDER] in the documents.", wraplength=760,
                  foreground="#666").pack(anchor="w", padx=14, pady=(2, 8))

    def load_profile(self, initial=False):
        p = Profile.load(PROFILE_PATH)
        for k, var in self.vars.items():
            var.set(getattr(p, k, ""))
        self.ht_var.set(p.harassment_type.value)
        self._safe_numbers = list(p.safe_numbers)
        if hasattr(self, "wl_tree"):
            self._wl_refresh()
        if not initial:
            self._log(f"Loaded profile from {os.path.abspath(PROFILE_PATH)}\n")

    def save_profile(self) -> Profile:
        p = Profile(
            harassment_type=HarassmentType.parse(self.ht_var.get()),
            safe_numbers=list(getattr(self, "_safe_numbers", [])),
            **{k: v.get().strip() for k, v in self.vars.items()},
        )
        path = p.save(PROFILE_PATH)
        self._log(f"Saved profile to {path}\n")
        return p

    # ------------------------------------------------------------ Generate --
    def _build_gen_tab(self):
        pad = {"padx": 10, "pady": 6}
        f = self.gen_tab
        ttk.Label(f, text="Build documents from iphone_calls.csv + your info.",
                  font=("Segoe UI", 10, "bold")).pack(anchor="w", **pad)
        ttk.Button(f, text="Build full evidence packet  (FCC · police · carrier · timeline · summary)",
                   command=self.build_packet).pack(fill="x", **pad)
        ttk.Button(f, text="Charts + one-page PDF only",
                   command=self.build_summary).pack(fill="x", **pad)
        ttk.Button(f, text="Open results folder", command=self.open_results).pack(fill="x", **pad)
        ttk.Label(f, text=f"Documents are written to {os.path.abspath(OUT_DIR)}.",
                  foreground="#666").pack(anchor="w", **pad)

    def _need_csv(self) -> bool:
        if not os.path.isfile(CSV_PATH):
            messagebox.showwarning("No call data", "Run Backup → Extract call history first.")
            return False
        return True

    def build_packet(self):
        if not self._need_csv():
            return
        profile = self.save_profile()
        if not profile.is_ready_for_documents:
            if not messagebox.askyesno("Missing info", "Name and phone are blank — the documents will "
                                       "show [PLACEHOLDER]s. Generate anyway?"):
                return

        def work():
            import packet
            rows = packet.rows_from_csv(CSV_PATH)
            written = packet.generate_all(rows, profile, OUT_DIR, IPHONE_SOURCE_NOTE)
            print(f"{len(rows)} calls  ->  {os.path.abspath(OUT_DIR)}")
            for name, p in written.items():
                print(f"  {os.path.basename(p)}")

        self._run(work, "Building evidence packet…")

    def build_summary(self):
        if not self._need_csv():
            return

        def work():
            import analyze_calls
            analyze_calls.analyze(os.path.abspath(CSV_PATH), None, os.path.abspath(OUT_DIR),
                                  {"duration_unit": "auto"})

        self._run(work, "Building charts + one-page PDF…")

    def open_results(self):
        os.makedirs(OUT_DIR, exist_ok=True)
        path = os.path.abspath(OUT_DIR)
        if os.name == "nt":
            os.startfile(path)  # noqa: S606
        elif sys.platform == "darwin":
            subprocess.Popen(["open", path])
        else:
            subprocess.Popen(["xdg-open", path])
        self._log(f"Opened {path}\n")


if __name__ == "__main__":
    App().mainloop()
