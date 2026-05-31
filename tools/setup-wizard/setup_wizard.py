#!/usr/bin/env python3
"""
BuildingBox Setup Wizard — a small Tkinter GUI over secrets_core.py.

Edit every secrets/config.json value in form fields, then:
  • Init             create/merge config.json from the README template
  • Reload           re-read config.json into the fields
  • Update & Restore  save the fields to config.json, then regenerate the real
                      (gitignored) secret files the build needs
  • Export           fold existing local secret files back into config.json and
                      write secrets/github-secrets.txt

A terminal pane streams what each action does. Pure stdlib — runs on macOS and
Windows from any Python 3.8+ with Tkinter (bundled with the standard installer).

Run:  python3 setup_wizard.py     (or double-click run.command / run.bat)
"""

from __future__ import annotations

import json
import queue
import threading
import tkinter as tk
from tkinter import filedialog, messagebox, scrolledtext, ttk

import secrets_core as core

PLACEHOLDER = core.PLACEHOLDER


class SetupWizard:
    def __init__(self, root: tk.Tk) -> None:
        self.root = root
        self.paths = core.Paths()
        self.log_queue: "queue.Queue[str | None]" = queue.Queue()
        self.busy = False

        root.title("BuildingBox Setup Wizard")
        root.geometry("1180x780")
        root.minsize(900, 560)

        # Scalar field name -> entry var; gsj handled separately as raw JSON.
        self.vars: dict[str, tk.StringVar] = {}

        self._build_toolbar()
        # Horizontal split: form on the left, terminal on the right (draggable divider).
        self.body = ttk.PanedWindow(self.root, orient="horizontal")
        self.body.pack(fill="both", expand=True)
        self.left = ttk.Frame(self.body)
        self.right = ttk.Frame(self.body)
        self.body.add(self.left, weight=3)
        self.body.add(self.right, weight=2)
        self._build_form()
        self._build_terminal()

        self.root.after(80, self._drain_log)
        self._load_into_form(initial=True)

    # ------------------------------------------------------------------ UI --
    def _build_toolbar(self) -> None:
        bar = ttk.Frame(self.root, padding=(10, 8))
        bar.pack(fill="x")
        ttk.Label(bar, text="BuildingBox Setup Wizard", font=("", 14, "bold")).pack(side="left")
        self.btns: list[ttk.Button] = []
        for text, cmd in (
            ("Init", self.on_init),
            ("Reload", self.on_reload),
            ("Update & Restore", self.on_restore),
            ("Export", self.on_export),
        ):
            b = ttk.Button(bar, text=text, command=cmd)
            b.pack(side="right", padx=4)
            self.btns.append(b)

    def _build_form(self) -> None:
        outer = ttk.Frame(self.left, padding=(10, 0))
        outer.pack(fill="both", expand=True)

        canvas = tk.Canvas(outer, highlightthickness=0)
        scroll = ttk.Scrollbar(outer, orient="vertical", command=canvas.yview)
        self.form = ttk.Frame(canvas)
        self.form.bind("<Configure>", lambda e: canvas.configure(scrollregion=canvas.bbox("all")))
        canvas.create_window((0, 0), window=self.form, anchor="nw")
        canvas.configure(yscrollcommand=scroll.set)
        canvas.pack(side="left", fill="both", expand=True)
        scroll.pack(side="right", fill="y")

        # --- Keystore ---
        self._section("Keystore (signs the release APK)")
        self._entry("keystoreStorePassword", "Store password", secret=True)
        self._entry("keystoreKeyAlias", "Key alias")
        self._entry("keystoreKeyPassword", "Key password", secret=True)
        self._entry(
            "keystoreBase64", "Keystore base64 (leave CHANGE_ME to auto-generate)", secret=True,
        )
        ks_actions = ttk.Frame(self.form)
        ks_actions.pack(fill="x", padx=4, pady=(2, 6))
        ttk.Button(
            ks_actions, text="Generate new keystore…", command=self.on_generate_keystore
        ).pack(side="left")
        ttk.Label(
            ks_actions, text="creates a key, fills the fields with its base64 (no file left behind)",
            foreground="#888",
        ).pack(side="left", padx=8)

        # --- Desktop Firebase ---
        self._section("Desktop Firebase (desktop REST — optional)")
        self._entry("df.apiKey", "Web API key", secret=True)
        self._entry("df.databaseUrl", "Realtime Database URL")
        self._entry("df.projectId", "Project ID")

        # --- Google services (raw JSON paste or load from file) ---
        self._section("google-services.json (Android — paste, or load from file)")
        gsj_actions = ttk.Frame(self.form)
        gsj_actions.pack(fill="x", padx=4, pady=(0, 4))
        ttk.Button(
            gsj_actions, text="Load from file…", command=self.on_load_google_services
        ).pack(side="left")
        ttk.Label(
            gsj_actions, text="pick a google-services.json anywhere on this computer",
            foreground="#888",
        ).pack(side="left", padx=8)
        self.gsj_text = scrolledtext.ScrolledText(self.form, height=14, wrap="none", font=("Menlo", 10))
        self.gsj_text.pack(fill="both", expand=True, padx=4, pady=(2, 8))

    def _section(self, title: str) -> None:
        ttk.Label(self.form, text=title, font=("", 11, "bold")).pack(
            anchor="w", padx=4, pady=(10, 2)
        )
        ttk.Separator(self.form, orient="horizontal").pack(fill="x", padx=4, pady=(0, 4))

    def _entry(self, key: str, label: str, secret: bool = False) -> None:
        row = ttk.Frame(self.form)
        row.pack(fill="x", padx=4, pady=2)
        ttk.Label(row, text=label, width=42, anchor="w").pack(side="left")
        var = tk.StringVar()
        self.vars[key] = var
        entry = ttk.Entry(row, textvariable=var, show="•" if secret else "")
        entry.pack(side="left", fill="x", expand=True)
        if secret:
            shown = {"v": False}

            def toggle(e=entry, s=shown) -> None:
                s["v"] = not s["v"]
                e.configure(show="" if s["v"] else "•")

            ttk.Button(row, text="👁", width=3, command=toggle).pack(side="left", padx=(4, 0))

    def _build_terminal(self) -> None:
        frame = ttk.Frame(self.right, padding=(10, 6))
        frame.pack(fill="both", expand=True)
        header = ttk.Frame(frame)
        header.pack(fill="x")
        ttk.Label(header, text="Terminal", font=("", 11, "bold")).pack(side="left")
        ttk.Button(header, text="Clear", command=self._clear_terminal).pack(side="right")
        self.term = scrolledtext.ScrolledText(
            frame, height=12, state="disabled", bg="#11151c", fg="#d6deeb",
            insertbackground="#d6deeb", font=("Menlo", 11),
        )
        self.term.pack(fill="both", expand=True, pady=(2, 0))

    def _clear_terminal(self) -> None:
        self.term.configure(state="normal")
        self.term.delete("1.0", "end")
        self.term.configure(state="disabled")

    # -------------------------------------------------------------- logging --
    def log(self, msg: str) -> None:
        """Thread-safe: enqueue; the Tk main loop drains it."""
        self.log_queue.put(msg)

    def _drain_log(self) -> None:
        try:
            while True:
                msg = self.log_queue.get_nowait()
                if msg is None:
                    self._set_busy(False)
                    continue
                self.term.configure(state="normal")
                self.term.insert("end", msg + "\n")
                self.term.see("end")
                self.term.configure(state="disabled")
        except queue.Empty:
            pass
        self.root.after(80, self._drain_log)

    # ------------------------------------------------------------- form I/O --
    def _load_into_form(self, initial: bool = False) -> None:
        cfg = {}
        if self.paths.config.exists():
            try:
                cfg = core.load_config(self.paths)
            except json.JSONDecodeError as e:
                self.log(f"WARN: {self.paths.config} is not valid JSON ({e}).")
        df = cfg.get("desktopFirebase", {}) or {}
        mapping = {
            "keystoreStorePassword": cfg.get("keystoreStorePassword", ""),
            "keystoreKeyAlias": cfg.get("keystoreKeyAlias", ""),
            "keystoreKeyPassword": cfg.get("keystoreKeyPassword", ""),
            "keystoreBase64": cfg.get("keystoreBase64", ""),
            "df.apiKey": df.get("apiKey", ""),
            "df.databaseUrl": df.get("databaseUrl", ""),
            "df.projectId": df.get("projectId", ""),
        }
        for k, v in mapping.items():
            self.vars[k].set(v if isinstance(v, str) else "")
        gsj = cfg.get("googleServicesJson")
        self.gsj_text.delete("1.0", "end")
        if gsj is not None:
            self.gsj_text.insert("1.0", json.dumps(gsj, indent=2))
        if initial:
            if self.paths.config.exists():
                self.log(f"Loaded {self.paths.config}")
            else:
                self.log("No secrets/config.json yet — click Init to create it.")

    def _form_to_config(self) -> dict | None:
        """Merge form values into the on-disk config (preserving any extra keys)."""
        cfg = {}
        if self.paths.config.exists():
            try:
                cfg = core.load_config(self.paths)
            except json.JSONDecodeError as e:
                self.log(f"ERROR: {self.paths.config} is not valid JSON ({e}). Fix or Init first.")
                return None
        cfg["keystoreStorePassword"] = self.vars["keystoreStorePassword"].get()
        cfg["keystoreKeyAlias"] = self.vars["keystoreKeyAlias"].get()
        cfg["keystoreKeyPassword"] = self.vars["keystoreKeyPassword"].get()
        cfg["keystoreBase64"] = self.vars["keystoreBase64"].get() or PLACEHOLDER
        cfg.setdefault("desktopFirebase", {})
        cfg["desktopFirebase"]["apiKey"] = self.vars["df.apiKey"].get() or PLACEHOLDER
        cfg["desktopFirebase"]["databaseUrl"] = self.vars["df.databaseUrl"].get() or PLACEHOLDER
        cfg["desktopFirebase"]["projectId"] = self.vars["df.projectId"].get() or PLACEHOLDER
        raw = self.gsj_text.get("1.0", "end").strip()
        if raw:
            try:
                cfg["googleServicesJson"] = json.loads(raw)
            except json.JSONDecodeError as e:
                self.log(f"ERROR: google-services JSON is invalid ({e}). Fix it and retry.")
                return None
        return cfg

    def _save_config(self, cfg: dict) -> None:
        core._secure_write_text(self.paths.config, json.dumps(cfg, indent=2) + "\n")

    # ---------------------------------------------------------------- actions --
    def _set_busy(self, busy: bool) -> None:
        self.busy = busy
        for b in self.btns:
            b.configure(state="disabled" if busy else "normal")

    def _run_bg(self, fn) -> None:
        if self.busy:
            return
        self._set_busy(True)

        def worker() -> None:
            try:
                fn()
            except Exception as e:  # noqa: BLE001 - report, don't crash the UI
                self.log(f"UNEXPECTED ERROR: {e}")
            finally:
                self.log_queue.put(None)  # sentinel → re-enable buttons

        threading.Thread(target=worker, daemon=True).start()

    def _divider(self, title: str) -> None:
        self.log("")
        self.log(f"━━ {title} ━━")

    def on_init(self) -> None:
        def task() -> None:
            self._divider("Init")
            core.init_config(self.paths, self.log)
            self.root.after(0, self._load_into_form)

        self._run_bg(task)

    def on_reload(self) -> None:
        self._divider("Reload")
        self._load_into_form()
        self.log(f"Reloaded {self.paths.config}" if self.paths.config.exists() else "No config to reload.")

    def on_restore(self) -> None:
        cfg = self._form_to_config()
        if cfg is None:
            return

        def task() -> None:
            self._divider("Update & Restore")
            self._save_config(cfg)
            self.log(f"Saved {self.paths.config}")
            core.restore_secrets(self.paths, self.log)

        self._run_bg(task)

    def on_export(self) -> None:
        def task() -> None:
            self._divider("Export")
            core.export_secrets(self.paths, self.log)
            self.root.after(0, self._load_into_form)

        self._run_bg(task)

    def on_load_google_services(self) -> None:
        """Pick a google-services.json from anywhere and load it into the paste box."""
        path = filedialog.askopenfilename(
            title="Select google-services.json",
            filetypes=[("JSON files", "*.json"), ("All files", "*.*")],
        )
        if not path:
            return
        self._divider("Load google-services.json")
        try:
            with open(path, "r", encoding="utf-8") as f:
                data = json.load(f)
        except (OSError, json.JSONDecodeError) as e:
            self.log(f"ERROR: could not read JSON from {path} ({e}).")
            messagebox.showerror("Invalid file", f"Could not read JSON:\n{e}")
            return
        # Light sanity check — warn but still load if it doesn't look like one.
        if "project_info" not in data:
            if not messagebox.askyesno(
                "Doesn't look like google-services.json",
                "This file has no 'project_info' key. Load it anyway?",
            ):
                return
        self.gsj_text.delete("1.0", "end")
        self.gsj_text.insert("1.0", json.dumps(data, indent=2))
        proj = (data.get("project_info") or {}).get("project_id", "?")
        self.log(f"Loaded {path}")
        self.log(f"project_id = {proj}.  Click Update & Restore to apply.")

    def on_generate_keystore(self) -> None:
        """Open a dialog to collect keystore credentials, warn, then generate a
        keystore, keep only its base64 in the fields, and discard the file."""
        dlg = KeystoreDialog(self.root, self.vars)
        self.root.wait_window(dlg.top)
        if not dlg.result:
            return
        store_pw, alias, key_pw, dname = dlg.result

        def task() -> None:
            self._divider("Generate new keystore")
            b64 = core.generate_keystore_base64(store_pw, alias, key_pw, dname, self.log)
            if b64 is None:
                return
            # Fill the form fields on the UI thread.
            def fill() -> None:
                self.vars["keystoreStorePassword"].set(store_pw)
                self.vars["keystoreKeyAlias"].set(alias)
                self.vars["keystoreKeyPassword"].set(key_pw)
                self.vars["keystoreBase64"].set(b64)
            self.root.after(0, fill)
            self.log("Fields filled with the new keystore's credentials + base64.")
            self.log("⚠ BACK UP these values — a release signing key is irreplaceable.")
            self.log("Click Update & Restore to write the secret files.")

        self._run_bg(task)


class KeystoreDialog:
    """Modal dialog collecting keystore credentials + a distinguished name.
    On accept (after a confirmation warning) sets `result` to
    (store_pw, alias, key_pw, dname); otherwise leaves it None."""

    def __init__(self, parent: tk.Tk, current: dict[str, tk.StringVar]) -> None:
        self.result: tuple[str, str, str, str] | None = None
        top = self.top = tk.Toplevel(parent)
        top.title("Generate new keystore")
        top.transient(parent)
        top.grab_set()
        top.resizable(False, False)

        pad = {"padx": 10, "pady": 4}
        ttk.Label(
            top,
            text="Create a NEW release signing key.\nThe keystore file is discarded — only its base64 is kept in the config.",
            foreground="#444", justify="left",
        ).grid(row=0, column=0, columnspan=2, sticky="w", **pad)

        # Pre-fill from current fields where sensible.
        self.store_pw = tk.StringVar(value=current["keystoreStorePassword"].get())
        self.alias = tk.StringVar(value=current["keystoreKeyAlias"].get() or "buildingbox")
        self.key_pw = tk.StringVar(value=current["keystoreKeyPassword"].get())
        self.cn = tk.StringVar(value="BuildingBox")
        self.ou = tk.StringVar(value="Dev")
        self.org = tk.StringVar(value="BuildingBox")
        self.country = tk.StringVar(value="US")

        rows = [
            ("Store password (min 6 chars)", self.store_pw, True),
            ("Key alias", self.alias, False),
            ("Key password (min 6 chars)", self.key_pw, True),
            ("Name (CN)", self.cn, False),
            ("Org unit (OU)", self.ou, False),
            ("Organization (O)", self.org, False),
            ("Country (C, 2 letters)", self.country, False),
        ]
        for i, (label, var, secret) in enumerate(rows, start=1):
            ttk.Label(top, text=label, width=26, anchor="w").grid(row=i, column=0, sticky="w", **pad)
            entry = ttk.Entry(top, textvariable=var, show="•" if secret else "", width=34)
            entry.grid(row=i, column=1, sticky="we", **pad)
            if secret:
                shown = {"v": False}

                def toggle(e=entry, s=shown) -> None:
                    s["v"] = not s["v"]
                    e.configure(show="" if s["v"] else "•")

                ttk.Button(top, text="👁", width=3, command=toggle).grid(row=i, column=2, padx=(0, 8))

        btns = ttk.Frame(top)
        btns.grid(row=len(rows) + 1, column=0, columnspan=2, sticky="e", padx=10, pady=(8, 10))
        ttk.Button(btns, text="Cancel", command=top.destroy).pack(side="right", padx=4)
        ttk.Button(btns, text="Generate", command=self._accept).pack(side="right", padx=4)

    def _accept(self) -> None:
        store_pw, alias, key_pw = self.store_pw.get(), self.alias.get().strip(), self.key_pw.get()
        if len(store_pw) < 6 or len(key_pw) < 6:
            messagebox.showerror("Invalid", "Store and key passwords must be at least 6 characters.", parent=self.top)
            return
        if not alias:
            messagebox.showerror("Invalid", "Key alias is required.", parent=self.top)
            return
        country = self.country.get().strip() or "US"
        dname = f"CN={self.cn.get().strip() or 'BuildingBox'}, OU={self.ou.get().strip() or 'Dev'}, O={self.org.get().strip() or 'BuildingBox'}, C={country}"
        if not messagebox.askyesno(
            "Confirm — generate new signing key",
            "This creates a brand-new release signing key and overwrites the keystore "
            "fields in the form.\n\n"
            "⚠ If you publish with this key, it becomes IRREPLACEABLE — losing it means "
            "you can never update the app on the store under the same identity.\n\n"
            "Generate it now?",
            parent=self.top,
        ):
            return
        self.result = (store_pw, alias, key_pw, dname)
        self.top.destroy()


def main() -> None:
    root = tk.Tk()
    SetupWizard(root)
    root.mainloop()


if __name__ == "__main__":
    main()
