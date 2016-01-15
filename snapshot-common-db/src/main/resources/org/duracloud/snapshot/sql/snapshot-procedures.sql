-- Purges data from the spring batch tables that is older than the number
-- of days specified in the days_ago parameter or is associated with a 
-- snapshot or restore that has been removed from the system.
-- @author Daniel Bernstein
-- @date 01/15/2016
delimiter //
DROP PROCEDURE IF EXISTS purge_obsolete_batch_data;

CREATE PROCEDURE purge_obsolete_batch_data( IN days_ago INT)
BEGIN
    set foreign_key_checks=0;
    delete from BATCH_JOB_EXECUTION where create_time < adddate(now(), -1*days_ago);


    delete je from BATCH_JOB_EXECUTION je join 
         (select distinct a.job_execution_id from BATCH_JOB_EXECUTION_PARAMS a, 
		                                  BATCH_JOB_INSTANCE b, 
						  BATCH_JOB_EXECUTION c  
					      where a.job_execution_id = c.job_execution_id and 
					            b.job_instance_id = c.job_instance_id and 
						    b.job_name = 'snapshot' and 
						    a.string_val not in (select name from snapshot)) je1 
					  on je.job_execution_id = je1.job_execution_id;

    delete je from BATCH_JOB_EXECUTION je join 
         (select a.job_execution_id from BATCH_JOB_EXECUTION_PARAMS a, 
		                         BATCH_JOB_INSTANCE b, 
					 BATCH_JOB_EXECUTION c  
					 where a.job_execution_id = c.job_execution_id and 
					       b.job_instance_id = c.job_instance_id and 
					       b.job_name = 'restore' and 
					       a.string_val not in (select restoration_id from restoration)) je1 
				         on je.job_execution_id = je1.job_execution_id; 

    delete from BATCH_JOB_INSTANCE  where job_instance_id not in (select job_instance_id from BATCH_JOB_EXECUTION);
    delete from BATCH_JOB_EXECUTION_CONTEXT where job_execution_id not in (select job_execution_id from BATCH_JOB_EXECUTION);
    delete from BATCH_JOB_EXECUTION_PARAMS where job_execution_id not in (select job_execution_id from BATCH_JOB_EXECUTION);
    delete from BATCH_STEP_EXECUTION  where job_execution_id not in (select job_execution_id from BATCH_JOB_EXECUTION);
    delete from BATCH_STEP_EXECUTION_CONTEXT  where step_execution_id not in (select step_execution_id from BATCH_STEP_EXECUTION);
    
    set foreign_key_checks=1;
END//
