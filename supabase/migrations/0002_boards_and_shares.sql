-- Airport boards fetched by any signed-in Contrail user, kept so one fetch serves everyone for
-- 15 minutes. Raw AeroDataBox JSON; their terms allow keeping results up to 7 days.
create table if not exists public.boards (
  airport    text primary key check (airport ~ '^[A-Z0-9]{3}$'),
  fetched_at bigint not null,
  body       text   not null
);

alter table public.boards enable row level security;

drop policy if exists "boards read" on public.boards;
create policy "boards read" on public.boards for select to authenticated using (true);

drop policy if exists "boards write" on public.boards;
create policy "boards write" on public.boards for insert to authenticated with check (true);

drop policy if exists "boards update" on public.boards;
create policy "boards update" on public.boards for update to authenticated using (true) with check (true);

-- A flight or trip one person shares with another. The sender keeps it current; the
-- recipient sees it under "Shared with you" and can follow it or add it to their own log.
create table if not exists public.shares (
  from_user     uuid    not null default auth.uid() references auth.users (id) on delete cascade,
  from_username text    not null,
  to_user       uuid    not null references auth.users (id) on delete cascade,
  kind          text    not null check (kind in ('flight', 'trip')),
  ref           text    not null,
  data          jsonb   not null,
  updated_at    bigint  not null,
  deleted       boolean not null default false,
  seq           bigint  not null default nextval('public.records_seq'),
  primary key (from_user, to_user, kind, ref)
);

create index if not exists shares_to_seq on public.shares (to_user, seq);

drop trigger if exists shares_bump_seq on public.shares;
create trigger shares_bump_seq before insert or update on public.shares
  for each row execute function public.records_bump_seq();

alter table public.shares enable row level security;

drop policy if exists "shares visible to both" on public.shares;
create policy "shares visible to both" on public.shares for select to authenticated
  using (to_user = auth.uid() or from_user = auth.uid());

drop policy if exists "shares sent by me" on public.shares;
create policy "shares sent by me" on public.shares for insert to authenticated with check (from_user = auth.uid());

drop policy if exists "shares updated by me" on public.shares;
create policy "shares updated by me" on public.shares for update to authenticated
  using (from_user = auth.uid()) with check (from_user = auth.uid());
