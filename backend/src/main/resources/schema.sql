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

-- Echo learner preferences, teacher-reviewed language lessons, and practice records.
create table if not exists echo_learner_profiles (
  account_id varchar(36) primary key references accounts(id),
  language_code varchar(8) not null default 'da',
  goal varchar(300) not null,
  self_reported_level varchar(32) not null,
  diagnostic_score integer not null,
  starting_route varchar(24) not null,
  created_at timestamp with time zone not null default current_timestamp,
  updated_at timestamp with time zone not null default current_timestamp
);
create table if not exists echo_lessons (
  id varchar(120) primary key,
  language_code varchar(8) not null default 'da',
  review_status varchar(24) not null default 'DRAFT',
  version integer not null default 1,
  revision_of varchar(120) references echo_lessons(id),
  created_by varchar(36) references accounts(id),
  approved_by varchar(36) references accounts(id),
  approved_at timestamp with time zone,
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
alter table echo_learner_profiles add column if not exists language_code varchar(8) not null default 'da';
alter table echo_lessons add column if not exists language_code varchar(8) not null default 'da';
alter table echo_lessons add column if not exists review_status varchar(24) not null default 'DRAFT';
alter table echo_lessons add column if not exists version integer not null default 1;
alter table echo_lessons add column if not exists revision_of varchar(120) references echo_lessons(id);
alter table echo_lessons add column if not exists created_by varchar(36) references accounts(id);
alter table echo_lessons add column if not exists approved_by varchar(36) references accounts(id);
alter table echo_lessons add column if not exists approved_at timestamp with time zone;
create index if not exists echo_lessons_language_review on echo_lessons(language_code,review_status,sort_order);
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
create table if not exists echo_exercise_answers (
  exercise_id varchar(120) not null references echo_exercises(id),
  answer_text varchar(500) not null,
  sort_order integer not null,
  primary key(exercise_id, sort_order)
);
create table if not exists echo_exercise_options (
  exercise_id varchar(120) not null references echo_exercises(id),
  option_key varchar(16) not null,
  option_text varchar(500) not null,
  sort_order integer not null,
  primary key(exercise_id, option_key)
);

-- Rights-aware music and podcast lessons remain private until teacher approval.
create table if not exists echo_audio_lessons (
  lesson_id varchar(120) primary key references echo_lessons(id),
  media_type varchar(16) not null,
  source_title varchar(240) not null,
  creator varchar(240) not null,
  source_url varchar(1500) not null,
  playback_url varchar(1500),
  editorial_band varchar(16) not null,
  topic_tags varchar(500) not null,
  speech_rate integer not null,
  vocabulary_density integer not null,
  idiom_density integer not null,
  pronunciation_load integer not null,
  topic_familiarity integer not null,
  before_listening_prompt varchar(1000) not null,
  gist_prompt varchar(1000) not null,
  focus_prompt varchar(1000) not null,
  transcript_text varchar(5000),
  rights_basis varchar(24) not null,
  link_allowed boolean not null default false,
  playback_allowed boolean not null default false,
  transcript_allowed boolean not null default false,
  rights_reference varchar(500) not null,
  rights_expires_at date
);
create table if not exists echo_audio_attempts (
  id varchar(36) primary key,
  account_id varchar(36) not null references accounts(id),
  lesson_id varchar(120) not null references echo_audio_lessons(lesson_id),
  task_type varchar(24) not null,
  response_chars integer not null,
  transcript_used boolean not null,
  confidence integer not null,
  created_at timestamp with time zone not null default current_timestamp
);
create index if not exists echo_audio_attempts_account_created on echo_audio_attempts(account_id, created_at);

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

-- Google OAuth support
alter table accounts add column if not exists google_sub varchar(255);
create unique index if not exists accounts_google_sub on accounts(google_sub);
alter table accounts alter column password_hash drop not null;
create table if not exists oauth_login_codes (
  code varchar(64) primary key, account_id varchar(36) not null references accounts(id),
  expires_at timestamp with time zone not null
);

-- Email verification: Google-verified accounts count as verified.
alter table accounts add column if not exists email_verified boolean not null default false;
update accounts set email_verified=true where google_sub is not null and not email_verified;
create table if not exists email_verification_tokens (
  token_hash varchar(64) primary key, account_id varchar(36) not null references accounts(id),
  expires_at timestamp with time zone not null,
  created_at timestamp with time zone not null default current_timestamp
);

-- Phrase cards learners write for themselves, reviewed on a calm Leitner schedule (box 0–5).
create table if not exists echo_cards (
  id varchar(36) primary key,
  account_id varchar(36) not null references accounts(id),
  language_code varchar(8) not null,
  front varchar(300) not null,
  back varchar(300) not null,
  lesson_id varchar(120) references echo_lessons(id),
  review_box integer not null default 0,
  next_review_at timestamp with time zone not null,
  reviewed_at timestamp with time zone,
  created_at timestamp with time zone not null default current_timestamp
);
create index if not exists echo_cards_account_language on echo_cards(account_id, language_code, next_review_at);
alter table echo_cards add column if not exists miss_count integer not null default 0;
alter table echo_cards add column if not exists last_miss varchar(300);

-- Optional personal details owned by the Deepwater account (shared by Echo and future products).
create table if not exists person_profiles (
  account_id varchar(36) primary key references accounts(id),
  phone varchar(16),
  city varchar(120),
  country varchar(80),
  native_language varchar(60),
  about varchar(500),
  updated_at timestamp with time zone not null default current_timestamp
);

-- Harbor: a talent profile built from the person's CV, job postings from public sources (or clearly
-- marked samples), and the person's own application tracker. Deleted with the account.
create table if not exists harbor_profiles (
  account_id varchar(36) primary key references accounts(id),
  headline varchar(200),
  summary varchar(2000),
  skills varchar(2000) not null default '',
  years_experience integer,
  languages varchar(500) not null default '',
  citizenship varchar(16) not null default 'UNKNOWN',
  target_countries varchar(200) not null default '',
  remote_preference varchar(16) not null default 'ANY',
  cv_file_name varchar(255),
  cv_text varchar(60000),
  updated_at timestamp with time zone not null default current_timestamp
);
create table if not exists harbor_jobs (
  id varchar(200) primary key,
  source varchar(32) not null,
  title varchar(300) not null,
  company varchar(200) not null,
  country_code varchar(2),
  location varchar(200),
  remote boolean not null default false,
  visa_signal varchar(16) not null default 'UNKNOWN',
  description varchar(20000) not null,
  tags varchar(1000) not null default '',
  url varchar(1500),
  posted_at timestamp with time zone,
  fetched_at timestamp with time zone not null default current_timestamp
);
create index if not exists harbor_jobs_country on harbor_jobs(country_code);
create table if not exists harbor_applications (
  id varchar(36) primary key,
  account_id varchar(36) not null references accounts(id),
  job_id varchar(200) not null references harbor_jobs(id),
  status varchar(16) not null,
  note varchar(1000),
  created_at timestamp with time zone not null default current_timestamp,
  updated_at timestamp with time zone not null default current_timestamp,
  unique(account_id, job_id)
);
-- Styling and formatting check of the last uploaded CV, as JSON (CvCheck).
alter table harbor_profiles add column if not exists cv_check varchar(8000);
-- Polish B1 practice exam attempts. result is the whole marked attempt as JSON (ExamService.Result).
create table if not exists exam_attempts (
  id varchar(36) primary key,
  account_id varchar(36) not null references accounts(id),
  exam_id varchar(40) not null,
  eligible boolean not null,
  level_estimate varchar(8) not null,
  average_percent integer not null,
  result varchar(60000) not null,
  created_at timestamp with time zone not null default current_timestamp
);
create index if not exists exam_attempts_account on exam_attempts(account_id, created_at);
