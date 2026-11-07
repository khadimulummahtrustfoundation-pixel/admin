-- NafsGuard D1 স্কিমা। প্রতিটা অংশ আলাদা বক্সে চালাবেন।

-- ১. users
CREATE TABLE IF NOT EXISTS users (
  phone10    TEXT PRIMARY KEY,
  phone      TEXT NOT NULL,
  name       TEXT NOT NULL,
  token      TEXT NOT NULL DEFAULT '',
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);

-- ২. rules
CREATE TABLE IF NOT EXISTS rules (
  package   TEXT PRIMARY KEY,
  reel_ids  TEXT NOT NULL DEFAULT '[]',
  skip_ids  TEXT NOT NULL DEFAULT '[]',
  reel_desc TEXT NOT NULL DEFAULT '[]'
);

-- ৩. app_config
CREATE TABLE IF NOT EXISTS app_config (
  key   TEXT PRIMARY KEY,
  value TEXT NOT NULL
);

-- ৪. push_log
CREATE TABLE IF NOT EXISTS push_log (
  id     INTEGER PRIMARY KEY AUTOINCREMENT,
  ts     INTEGER NOT NULL,
  phone  TEXT NOT NULL DEFAULT '',
  cmd    TEXT NOT NULL,
  detail TEXT NOT NULL DEFAULT '',
  result TEXT NOT NULL DEFAULT ''
);

-- ৫. ভার্সনের শুরুর মান (আগে থাকলে বদলাবে না)
INSERT OR IGNORE INTO app_config(key,value) VALUES
  ('latest_version_code','4'),
  ('latest_version_name','2.1'),
  ('update_url','https://nafsguard.netlify.app/');
