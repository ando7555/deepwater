create table if not exists accounts (
  id varchar(36) primary key, name varchar(160) not null, email varchar(320) unique not null, password_hash varchar(100) not null,
  created_at timestamp with time zone not null default current_timestamp
);
create table if not exists user_sessions (
  token varchar(64) primary key, account_id varchar(36) not null references accounts(id),
  expires_at timestamp with time zone not null
);
create table if not exists audit_events (
  id varchar(36) primary key, event_type varchar(120) not null,
  aggregate_id varchar(36) not null, occurred_at timestamp with time zone not null default current_timestamp
);

-- Echo's first Danish learning slice. Source notes are retained on every lesson;
-- personal class notes and unlicensed media are deliberately excluded.
create table if not exists echo_learner_profiles (
  account_id varchar(36) primary key references accounts(id),
  goal varchar(300) not null,
  self_reported_level varchar(32) not null,
  diagnostic_score integer not null,
  starting_route varchar(24) not null,
  created_at timestamp with time zone not null default current_timestamp,
  updated_at timestamp with time zone not null default current_timestamp
);
create table if not exists echo_lessons (
  id varchar(120) primary key,
  title varchar(240) not null,
  level varchar(32) not null,
  topic varchar(120) not null,
  objective varchar(500) not null,
  explanation varchar(3000) not null,
  source_reference varchar(500) not null,
  content_status varchar(120) not null,
  skill_key varchar(120) not null,
  sort_order integer not null
);
create table if not exists echo_exercises (
  id varchar(120) primary key,
  lesson_id varchar(120) not null references echo_lessons(id),
  position integer not null,
  task_type varchar(24) not null,
  prompt varchar(1000) not null,
  expected_answer varchar(500),
  feedback_correct varchar(1000) not null,
  feedback_incorrect varchar(1000) not null,
  unique(lesson_id, position)
);
create table if not exists echo_exercise_options (
  exercise_id varchar(120) not null references echo_exercises(id),
  option_key varchar(16) not null,
  option_text varchar(500) not null,
  sort_order integer not null,
  primary key(exercise_id, option_key)
);
create table if not exists echo_attempts (
  id varchar(36) primary key,
  account_id varchar(36) not null references accounts(id),
  exercise_id varchar(120) not null references echo_exercises(id),
  answer varchar(2000) not null,
  is_correct boolean,
  is_transfer boolean not null default false,
  created_at timestamp with time zone not null default current_timestamp
);
create index if not exists echo_attempts_account_created on echo_attempts(account_id, created_at);
create table if not exists echo_skill_states (
  account_id varchar(36) not null references accounts(id),
  skill_key varchar(120) not null,
  mastery decimal(5,4) not null default 0,
  correct_streak integer not null default 0,
  interval_days integer not null default 1,
  next_review_at timestamp with time zone,
  attempts integer not null default 0,
  updated_at timestamp with time zone not null default current_timestamp,
  primary key(account_id, skill_key)
);
