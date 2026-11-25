-- 021 (conception du 20/11/2026) : passage cité d'une remarque notée sur une sélection du document.
alter table interview_point add column quote text check (quote is null or length(quote) <= 1000);
