CREATE OR REPLACE FUNCTION delete_my_account()
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER -- This allows the function to bypass RLS and delete from auth.users
AS $$
BEGIN
  -- Ensure the user is actually authenticated
  IF auth.uid() IS NULL THEN
    RAISE EXCEPTION 'Not authenticated';
  END IF;

  -- Delete the user from auth.users. 
  -- Note: If you have ON DELETE CASCADE set up on your user_profiles, 
  -- posture_logs, and crash_reports tables, this will also automatically 
  -- wipe all their data!
  DELETE FROM auth.users WHERE id = auth.uid();
END;
$$;
