-- liquibase formatted sql

-- changeset bidnow:jobrunr_init
CREATE TABLE public.jobrunr_migrations
(
    id          bpchar(36)  NOT NULL,
    script      varchar(64) NOT NULL,
    installedon varchar(29) NOT NULL,
    CONSTRAINT jobrunr_migrations_pkey PRIMARY KEY (id)
);

CREATE TABLE public.jobrunr_jobs
(
    id             bpchar(36)   NOT NULL,
    version        int          NOT NULL,
    jobasjson      text         NOT NULL,
    jobsignature   varchar(512) NOT NULL,
    state          varchar(36)  NOT NULL,
    createdat      timestamp    NOT NULL,
    updatedat      timestamp    NOT NULL,
    scheduledat    timestamp NULL,
    recurringjobid varchar(128) NULL,
    CONSTRAINT jobrunr_jobs_pkey PRIMARY KEY (id)
);
CREATE INDEX jobrunr_state_idx ON public.jobrunr_jobs (state);
CREATE INDEX jobrunr_job_signature_idx ON public.jobrunr_jobs (jobsignature);
CREATE INDEX jobrunr_job_created_at_idx ON public.jobrunr_jobs (createdat);
CREATE INDEX jobrunr_job_scheduled_at_idx ON public.jobrunr_jobs (scheduledat);
CREATE INDEX jobrunr_job_rci_idx ON public.jobrunr_jobs (recurringjobid);
CREATE INDEX jobrunr_jobs_state_updated_idx ON public.jobrunr_jobs (state ASC, updatedat ASC);

CREATE TABLE public.jobrunr_recurring_jobs
(
    id        bpchar(128) NOT NULL,
    version   int    NOT NULL,
    jobasjson text   NOT NULL,
    createdat bigint NOT NULL DEFAULT 0,
    CONSTRAINT jobrunr_recurring_jobs_pkey PRIMARY KEY (id)
);
CREATE INDEX jobrunr_recurring_job_created_at_idx ON public.jobrunr_recurring_jobs (createdat);

CREATE TABLE public.jobrunr_backgroundjobservers
(
    id                         bpchar(36)    NOT NULL,
    workerpoolsize             int           NOT NULL,
    pollintervalinseconds      int           NOT NULL,
    firstheartbeat             timestamp(6)  NOT NULL,
    lastheartbeat              timestamp(6)  NOT NULL,
    running                    int           NOT NULL,
    systemtotalmemory          bigint        NOT NULL,
    systemfreememory           bigint        NOT NULL,
    systemcpuload              numeric(3, 2) NOT NULL,
    processmaxmemory           bigint        NOT NULL,
    processfreememory          bigint        NOT NULL,
    processallocatedmemory     bigint        NOT NULL,
    processcpuload             numeric(3, 2) NOT NULL,
    deletesucceededjobsafter   varchar(32) NULL,
    permanentlydeletejobsafter varchar(32) NULL,
    name                       varchar(128) NULL,
    CONSTRAINT jobrunr_backgroundjobservers_pkey PRIMARY KEY (id)
);
CREATE INDEX jobrunr_bgjobsrvrs_fsthb_idx ON public.jobrunr_backgroundjobservers (firstheartbeat);
CREATE INDEX jobrunr_bgjobsrvrs_lsthb_idx ON public.jobrunr_backgroundjobservers (lastheartbeat);

CREATE TABLE public.jobrunr_metadata
(
    id        varchar(156) NOT NULL,
    name      varchar(92)  NOT NULL,
    owner     varchar(64)  NOT NULL,
    value     text         NOT NULL,
    createdat timestamp    NOT NULL,
    updatedat timestamp    NOT NULL,
    CONSTRAINT jobrunr_metadata_pkey PRIMARY KEY (id)
);
INSERT INTO public.jobrunr_metadata (id, name, owner, value, createdat, updatedat)
VALUES ('succeeded-jobs-counter-cluster', 'succeeded-jobs-counter', 'cluster', '0', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);

CREATE VIEW public.jobrunr_jobs_stats AS
WITH job_stat_results AS (SELECT state, count(*) AS count
    FROM public.jobrunr_jobs
    GROUP BY ROLLUP (state
)
)
SELECT coalesce((SELECT count FROM job_stat_results WHERE state IS NULL), 0)        AS total,
       coalesce((SELECT count FROM job_stat_results WHERE state = 'SCHEDULED'), 0)  AS scheduled,
       coalesce((SELECT count FROM job_stat_results WHERE state = 'ENQUEUED'), 0)   AS enqueued,
       coalesce((SELECT count FROM job_stat_results WHERE state = 'PROCESSING'), 0) AS processing,
       coalesce((SELECT count FROM job_stat_results WHERE state = 'FAILED'), 0)     AS failed,
       coalesce((SELECT count FROM job_stat_results WHERE state = 'SUCCEEDED'), 0)  AS succeeded,
       coalesce((SELECT CAST(CAST(value AS CHAR(10)) AS NUMERIC(10, 0))
                 FROM public.jobrunr_metadata jm
                 WHERE jm.id = 'succeeded-jobs-counter-cluster'), 0)                AS allTimeSucceeded,
       coalesce((SELECT count FROM job_stat_results WHERE state = 'DELETED'), 0)    AS deleted,
       (SELECT count(*) FROM public.jobrunr_backgroundjobservers)                   AS nbrOfBackgroundJobServers,
       (SELECT count(*) FROM public.jobrunr_recurring_jobs)                         AS nbrOfRecurringJobs;
