-- Contrail sync: one row per synced thing (a trip with its flights, a lounge visit, a watchlist
-- item...). Rows belong to one user, and row-level security keeps everyone to their own.

create sequence if not exists public.records_seq;

create table if not exists public.records (
  user_id    uuid    not null default auth.uid() references auth.users (id) on delete cascade,
  kind       text    not null,
  id         text    not null,
  data       jsonb   not null,
  -- When the writing device made the change (ms). Informational; ordering uses seq.
  updated_at bigint  not null,
  deleted    boolean not null default false,
  -- Server-assigned on every write, so devices pull "everything after seq N" regardless of clocks.
  seq        bigint  not null default nextval('public.records_seq'),
  primary key (user_id, kind, id)
);

create index if not exists records_user_seq on public.records (user_id, seq);

create or replace function public.records_bump_seq() returns trigger
language plpgsql as $$
begin
  new.seq := nextval('public.records_seq');
  return new;
end $$;

drop trigger if exists records_bump_seq on public.records;
create trigger records_bump_seq before insert or update on public.records
  for each row execute function public.records_bump_seq();

alter table public.records enable row level security;

drop policy if exists "own records" on public.records;
create policy "own records" on public.records
  for all to authenticated
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

-- Usernames, so people can find each other to share a flight later.
create table if not exists public.profiles (
  id         uuid primary key references auth.users (id) on delete cascade,
  username   text unique not null check (username ~ '^[a-z0-9_.]{3,24}$'),
  created_at timestamptz not null default now()
);

alter table public.profiles enable row level security;

drop policy if exists "profiles readable" on public.profiles;
create policy "profiles readable" on public.profiles for select to authenticated using (true);

drop policy if exists "own profile" on public.profiles;
create policy "own profile" on public.profiles for insert to authenticated with check (id = auth.uid());
