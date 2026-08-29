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

from profile import DEFAULT_PATH as PROFILE_PATH, HarassmentType, Profile  # noqa: E402
import backup_locator as bl  # noqa: E402
import callhistory_parser as chp  # noqa: E402
from callhistory_parser import IPHONE_SOURCE_NOTE  # noqa: E402

CSV_PATH = "iphone_calls.csv"
OUT_DIR = "iphone_packet"

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

        nb = ttk.Notebook(self)
        nb.pack(fill="both", expand=True, padx=8, pady=(8, 4))
        self.backup_tab = ttk.Frame(nb)
        self.info_tab = ttk.Frame(nb)
        self.gen_tab = ttk.Frame(nb)
        nb.add(self.backup_tab, text="1 · Backup")
        nb.add(self.info_tab, text="2 · My info")
        nb.add(self.gen_tab, text="3 · Generate")

        self._build_backup_tab()
        self._build_info_tab()
        self._build_gen_tab()

        self.log = tk.Text(self, height=11, wrap="word", bg="#111", fg="#eee",
                           insertbackground="#eee")
        self.log.pack(fill="both", expand=False, padx=10, pady=(0, 10))
        self._log("Ready. Step 1: make a local backup of the iPhone with the Apple Devices "
                  "app / iTunes / Finder, then click Refresh.\n")

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

        ttk.Button(f, text="Extract call history  ➜  iphone_calls.csv",
                   command=self.extract).pack(fill="x", **pad)
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

        def work():
            import tempfile
            workdir = tempfile.mkdtemp(prefix="tw_iphone_")
            storedata = bl.extract_call_history(b, os.path.join(workdir, "ch.storedata"), pw)
            ab = bl.extract_address_book(b, os.path.join(workdir, "ab.sqlitedb"), pw)
            calls = chp.parse(storedata, ab, include_facetime=facetime)
            chp.write_csv(calls, CSV_PATH)
            flagged = sum(1 for c in calls if c.suspicious())
            print(f"{len(calls)} calls ({flagged} flagged) written to {os.path.abspath(CSV_PATH)}")
            if not calls:
                print("No calls found. The iPhone may not have call history synced to this backup.")

        self._run(work, f"Extracting call history from: {b.label()}")

    # ------------------------------------------------------------- My info --
    def _build_info_tab(self):
        f = self.info_tab
        grid = ttk.Frame(f)
        grid.pack(fill="x", padx=14, pady=10)

        def field(label, key, r, combo=None):
            ttk.Label(grid, text=label).grid(row=r, column=0, sticky="w", pady=4)
            var = tk.StringVar()
            self.vars[key] = var
            if combo is not None:
                w = ttk.Combobox(grid, textvariable=var, values=combo, width=38)
            else:
                w = ttk.Entry(grid, textvariable=var, width=40)
            w.grid(row=r, column=1, sticky="w", padx=8)

        field("Full name", "full_name", 0)
        field("Phone (your cell)", "phone", 1)
        field("Email", "email", 2)
        field("City", "address_city", 3)
        field("State", "state", 4, combo=STATES)
        field("Carrier", "carrier", 5, combo=CARRIERS)
        field("FCC complaint #", "fcc_complaint_number", 6)
        field("Police case #", "police_case_number", 7)
        field("Carrier case #", "carrier_case_number", 8)

        ttk.Label(grid, text="Harassment type").grid(row=9, column=0, sticky="w", pady=6)
        self.ht_var = tk.StringVar(value=HarassmentType.SILENT.value)
        htf = ttk.Frame(grid)
        htf.grid(row=9, column=1, sticky="w", padx=8)
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
        if not initial:
            self._log(f"Loaded profile from {os.path.abspath(PROFILE_PATH)}\n")

    def save_profile(self) -> Profile:
        p = Profile(
            harassment_type=HarassmentType.parse(self.ht_var.get()),
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
