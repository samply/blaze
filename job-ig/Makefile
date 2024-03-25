build:
	sushi build -s .
	cp fsh-generated/resources/CodeSystem-JobOutput.json ../modules/job-scheduler/resources/blaze/job_scheduler/CodeSystem-JobOutput.json
	cp fsh-generated/resources/CodeSystem-JobType.json ../modules/job-scheduler/resources/blaze/job_scheduler/CodeSystem-JobType.json
	cp fsh-generated/resources/StructureDefinition-Job.json ../modules/job-scheduler/resources/blaze/job_scheduler/StructureDefinition-Job.json
	cp fsh-generated/resources/CodeSystem-ReIndexJobOutput.json ../modules/job-re-index/resources/blaze/job/re_index/CodeSystem-ReIndexJobOutput.json
	cp fsh-generated/resources/CodeSystem-ReIndexJobParameter.json ../modules/job-re-index/resources/blaze/job/re_index/CodeSystem-ReIndexJobParameter.json
	cp fsh-generated/resources/StructureDefinition-ReIndexJob.json ../modules/job-re-index/resources/blaze/job/re_index/StructureDefinition-ReIndexJob.json

.PHONY: build
