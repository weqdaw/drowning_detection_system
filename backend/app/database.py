import sqlite3
import os
from datetime import datetime
from .config import settings

def get_db_connection():
    conn = sqlite3.connect(settings.DATABASE_PATH)
    conn.row_factory = sqlite3.Row
    return conn

import hashlib
import secrets

def hash_password(password: str, salt: str = None) -> str:
    if not salt:
        salt = secrets.token_hex(16)
    pbkdf2 = hashlib.pbkdf2_hmac(
        "sha256",
        password.encode("utf-8"),
        salt.encode("utf-8"),
        100000
    )
    return f"{salt}${pbkdf2.hex()}"

def verify_password(password: str, hashed_password: str) -> bool:
    try:
        salt, key = hashed_password.split("$")
        new_hash = hash_password(password, salt)
        return new_hash == hashed_password
    except Exception:
        return False

def init_db():
    conn = get_db_connection()
    cursor = conn.cursor()

    # 1. Streams table
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS streams (
        id TEXT PRIMARY KEY,
        name TEXT NOT NULL,
        input_url TEXT NOT NULL,
        is_active INTEGER DEFAULT 0,
        selected_model TEXT NOT NULL,
        confidence REAL DEFAULT 0.25,
        device TEXT DEFAULT 'cuda',
        rules_config TEXT,
        created_at TIMESTAMP DEFAULT (datetime('now', 'localtime'))
    )
    """)
    try:
        cursor.execute("ALTER TABLE streams ADD COLUMN device TEXT DEFAULT 'cuda'")
    except sqlite3.OperationalError:
        pass
    try:
        cursor.execute("ALTER TABLE streams ADD COLUMN rules_config TEXT")
    except sqlite3.OperationalError:
        pass

    # 2. Events table
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS events (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        stream_id TEXT NOT NULL,
        rule_type TEXT NOT NULL,
        event_name TEXT NOT NULL,
        details TEXT,
        tracker_id INTEGER,
        confidence REAL,
        status TEXT DEFAULT 'PENDING',
        handled_by TEXT,
        timestamp TIMESTAMP DEFAULT (datetime('now', 'localtime'))
    )
    """)
    try:
        cursor.execute("ALTER TABLE events ADD COLUMN confidence REAL")
    except sqlite3.OperationalError:
        pass
    try:
        cursor.execute("ALTER TABLE events ADD COLUMN status TEXT DEFAULT 'PENDING'")
    except sqlite3.OperationalError:
        pass
    try:
        cursor.execute("ALTER TABLE events ADD COLUMN handled_by TEXT")
    except sqlite3.OperationalError:
        pass

    # 2. Users table
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS users (
        username TEXT PRIMARY KEY,
        password_hash TEXT NOT NULL,
        role TEXT NOT NULL,
        created_at TIMESTAMP DEFAULT (datetime('now', 'localtime'))
    )
    """)

    # 3. Audit logs table
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS audit_logs (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        username TEXT,
        action TEXT NOT NULL,
        details TEXT,
        ip_address TEXT,
        timestamp TIMESTAMP DEFAULT (datetime('now', 'localtime'))
    )
    """)

    # 4. Recordings table
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS recordings (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        stream_id TEXT NOT NULL,
        file_name TEXT NOT NULL,
        file_path TEXT NOT NULL,
        duration REAL DEFAULT 0.0,
        file_size INTEGER DEFAULT 0,
        threshold REAL DEFAULT 0.70,
        max_confidence REAL DEFAULT 0.0,
        trigger_reason TEXT,
        created_at TIMESTAMP DEFAULT (datetime('now', 'localtime'))
    )
    """)

    # Seed default user if users table is empty
    cursor.execute("SELECT COUNT(*) FROM users")
    if cursor.fetchone()[0] == 0:
        admin_pass_hash = hash_password("admin123")
        cursor.execute(
            "INSERT INTO users (username, password_hash, role) VALUES (?, ?, ?)",
            ("admin", admin_pass_hash, "admin")
        )

    conn.commit()
    conn.close()

def get_user(username: str):
    conn = get_db_connection()
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM users WHERE username = ?", (username,))
    row = cursor.fetchone()
    conn.close()
    return dict(row) if row else None

def add_audit_log(username: str, action: str, details: str, ip_address: str):
    conn = get_db_connection()
    cursor = conn.cursor()
    now_str = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    cursor.execute(
        "INSERT INTO audit_logs (username, action, details, ip_address, timestamp) VALUES (?, ?, ?, ?, ?)",
        (username, action, details, ip_address, now_str)
    )
    conn.commit()
    conn.close()

def get_audit_logs(limit: int = 100):
    conn = get_db_connection()
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM audit_logs ORDER BY timestamp DESC LIMIT ?", (limit,))
    rows = cursor.fetchall()
    conn.close()
    return [dict(row) for row in rows]

def add_stream(stream_id: str, name: str, input_url: str, selected_model: str, confidence: float = 0.25, device: str = "cuda"):
    conn = get_db_connection()
    cursor = conn.cursor()
    cursor.execute(
        "INSERT OR REPLACE INTO streams (id, name, input_url, is_active, selected_model, confidence, device) VALUES (?, ?, ?, ?, ?, ?, ?)",
        (stream_id, name, input_url, 0, selected_model, confidence, device)
    )
    conn.commit()
    conn.close()

def get_all_streams():
    conn = get_db_connection()
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM streams ORDER BY created_at DESC")
    rows = cursor.fetchall()
    conn.close()
    return [dict(row) for row in rows]

def get_stream(stream_id: str):
    conn = get_db_connection()
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM streams WHERE id = ?", (stream_id,))
    row = cursor.fetchone()
    conn.close()
    return dict(row) if row else None

def update_stream_status(stream_id: str, is_active: bool):
    conn = get_db_connection()
    cursor = conn.cursor()
    cursor.execute("UPDATE streams SET is_active = ? WHERE id = ?", (1 if is_active else 0, stream_id))
    conn.commit()
    conn.close()

def delete_stream(stream_id: str):
    conn = get_db_connection()
    cursor = conn.cursor()
    cursor.execute("DELETE FROM streams WHERE id = ?", (stream_id,))
    conn.commit()
    conn.close()

def update_stream_model(stream_id: str, selected_model: str):
    conn = get_db_connection()
    cursor = conn.cursor()
    cursor.execute("UPDATE streams SET selected_model = ? WHERE id = ?", (selected_model, stream_id))
    conn.commit()
    conn.close()

def update_stream_device(stream_id: str, device: str):
    conn = get_db_connection()
    cursor = conn.cursor()
    cursor.execute("UPDATE streams SET device = ? WHERE id = ?", (device, stream_id))
    conn.commit()
    conn.close()

def update_stream_rules(stream_id: str, rules_config: str):
    conn = get_db_connection()
    cursor = conn.cursor()
    cursor.execute("UPDATE streams SET rules_config = ? WHERE id = ?", (rules_config, stream_id))
    conn.commit()
    conn.close()

def update_stream_confidence(stream_id: str, confidence: float):
    conn = get_db_connection()
    cursor = conn.cursor()
    cursor.execute("UPDATE streams SET confidence = ? WHERE id = ?", (confidence, stream_id))
    conn.commit()
    conn.close()

def add_event(stream_id: str, rule_type: str, event_name: str, details: str, tracker_id: int = None, confidence: float = None):
    conn = get_db_connection()
    cursor = conn.cursor()
    now_str = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    cursor.execute(
        "INSERT INTO events (stream_id, rule_type, event_name, details, tracker_id, confidence, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?)",
        (stream_id, rule_type, event_name, details, tracker_id, confidence, now_str)
    )
    conn.commit()
    conn.close()

def get_events(stream_id: str = None, status: str = None, limit: int = 100):
    conn = get_db_connection()
    cursor = conn.cursor()
    query = "SELECT * FROM events"
    params = []
    conditions = []
    if stream_id:
        conditions.append("stream_id = ?")
        params.append(stream_id)
    if status:
        conditions.append("status = ?")
        params.append(status)
    if conditions:
        query += " WHERE " + " AND ".join(conditions)
    query += " ORDER BY timestamp DESC LIMIT ?"
    params.append(limit)
    cursor.execute(query, tuple(params))
    rows = cursor.fetchall()
    conn.close()
    return [dict(row) for row in rows]

def update_event_status(event_id: int, status: str, handled_by: str = None):
    conn = get_db_connection()
    cursor = conn.cursor()
    cursor.execute(
        "UPDATE events SET status = ?, handled_by = ? WHERE id = ?",
        (status, handled_by, event_id)
    )
    conn.commit()
    conn.close()

def delete_event(event_id: int):
    conn = get_db_connection()
    cursor = conn.cursor()
    cursor.execute("DELETE FROM events WHERE id = ?", (event_id,))
    conn.commit()
    conn.close()

def delete_all_events():
    conn = get_db_connection()
    cursor = conn.cursor()
    cursor.execute("DELETE FROM events")
    conn.commit()
    conn.close()

def add_recording(stream_id: str, file_name: str, file_path: str, duration: float, file_size: int, threshold: float, max_confidence: float, trigger_reason: str):
    conn = get_db_connection()
    cursor = conn.cursor()
    now_str = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    cursor.execute(
        """INSERT INTO recordings
           (stream_id, file_name, file_path, duration, file_size, threshold, max_confidence, trigger_reason, created_at)
           VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
        (stream_id, file_name, file_path, duration, file_size, threshold, max_confidence, trigger_reason, now_str)
    )
    conn.commit()
    conn.close()

def get_recordings(stream_id: str = None, limit: int = 100):
    conn = get_db_connection()
    cursor = conn.cursor()
    if stream_id:
        cursor.execute("SELECT * FROM recordings WHERE stream_id = ? ORDER BY created_at DESC LIMIT ?", (stream_id, limit))
    else:
        cursor.execute("SELECT * FROM recordings ORDER BY created_at DESC LIMIT ?", (limit,))
    rows = cursor.fetchall()
    conn.close()
    return [dict(row) for row in rows]

def delete_recording(recording_id: int):
    conn = get_db_connection()
    cursor = conn.cursor()
    cursor.execute("SELECT file_path FROM recordings WHERE id = ?", (recording_id,))
    row = cursor.fetchone()
    file_path = row["file_path"] if row else None

    cursor.execute("DELETE FROM recordings WHERE id = ?", (recording_id,))
    conn.commit()
    conn.close()

    if file_path and os.path.exists(file_path):
        try:
            os.remove(file_path)
        except Exception:
            pass

def delete_all_recordings():
    conn = get_db_connection()
    cursor = conn.cursor()
    cursor.execute("SELECT file_path FROM recordings")
    rows = cursor.fetchall()

    cursor.execute("DELETE FROM recordings")
    conn.commit()
    conn.close()

    for row in rows:
        fpath = row["file_path"] if row else None
        if fpath and os.path.exists(fpath):
            try:
                os.remove(fpath)
            except Exception:
                pass
