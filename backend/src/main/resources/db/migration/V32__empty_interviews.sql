-- 021 : une relecture terminée sans aucun point n'est pas une séance (« Terminer » la supprime
-- désormais). Celles gardées avant disparaissent ; leurs spécifications suivent par ON DELETE CASCADE.
DELETE FROM interview
WHERE state = 'FINISHED'
  AND NOT EXISTS (SELECT 1 FROM interview_point p WHERE p.interview_id = interview.id);
