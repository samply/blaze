# FHIR Test Data

Generates with Synthea v2.7.0 using openjdk 11.

```sh
java -jar synthea-with-dependencies.jar -p 100 --exporter.use_uuid_filenames=true
```

```text
Scanned 66 modules and 67 submodules.
Loading submodule modules/breast_cancer/tnm_diagnosis.json
Loading submodule modules/allergies/allergy_incidence.json
Loading submodule modules/covid19/nonsurvivor_lab_values.json
Loading submodule modules/covid19/outcomes.json
Loading submodule modules/covid19/survivor_lab_values.json
Loading submodule modules/dermatitis/moderate_cd_obs.json
Loading submodule modules/covid19/diagnose_blood_clot.json
Loading submodule modules/dermatitis/severe_cd_obs.json
Loading submodule modules/contraceptives/female_sterilization.json
Loading submodule modules/allergies/outgrow_env_allergies.json
Loading submodule modules/covid19/medications.json
Loading submodule modules/encounter/vitals.json
Loading submodule modules/heart/chf_meds.json
Loading submodule modules/lung_cancer/lung_cancer_probabilities.json
Loading submodule modules/contraceptives/patch_contraceptive.json
Loading submodule modules/heart/chf_lvad.json
Loading submodule modules/allergies/allergy_panel.json
Loading submodule modules/covid19/supplies_hospitalization.json
Loading submodule modules/breast_cancer/surgery_therapy_breast.json
Loading submodule modules/allergies/severe_allergic_reaction.json
Loading submodule modules/covid19/supplies_intubation.json
Loading submodule modules/covid19/infection.json
Loading submodule modules/dermatitis/early_severe_eczema_obs.json
Loading submodule modules/total_joint_replacement/functional_status_assessments.json
Loading submodule modules/weight_loss/mend_week.json
Loading submodule modules/heart/chf_lab_work.json
Loading submodule modules/contraceptives/ring_contraceptive.json
Loading submodule modules/covid19/end_symptoms.json
Loading submodule modules/covid19/diagnose_bacterial_infection.json
Loading submodule modules/anemia/anemia_sub.json
Loading submodule modules/heart/chf_transplant.json
Loading submodule modules/medications/otc_pain_reliever.json
Loading submodule modules/breast_cancer/hormone_diagnosis.json
Loading submodule modules/covid19/supplies_icu.json
Loading submodule modules/medications/strong_opioid_pain_reliever.json
Loading submodule modules/medications/ear_infection_antibiotic.json
Loading submodule modules/allergies/immunotherapy.json
Loading submodule modules/dermatitis/mid_moderate_eczema_obs.json
Loading submodule modules/contraceptives/clear_contraceptive.json
Loading submodule modules/heart/chf_meds_hfmef.json
Loading submodule modules/covid19/end_outcomes.json
Loading submodule modules/heart/chf_nyha_panel.json
Loading submodule modules/surgery/general_anesthesia.json
Loading submodule modules/veterans/veteran_suicide_probabilities.json
Loading submodule modules/contraceptives/oral_contraceptive.json
Loading submodule modules/medications/moderate_opioid_pain_reliever.json
Loading submodule modules/covid19/measurements_vitals.json
Loading submodule modules/medications/otc_antihistamine.json
Loading submodule modules/covid19/determine_risk.json
Loading Lookup Table: covid-19-severity-outcomes.csv
Loading Lookup Table: covid-19-survival-outcomes.csv
Loading submodule modules/covid19/admission.json
Loading submodule modules/covid19/measurements_frequent.json
Loading submodule modules/dermatitis/mid_severe_eczema_obs.json
Loading submodule modules/dermatitis/early_moderate_eczema_obs.json
Loading submodule modules/covid19/symptoms.json
Loading submodule modules/contraceptives/intrauterine_device.json
Loading submodule modules/contraceptives/male_sterilization.json
Loading submodule modules/heart/chf_meds_hfref_nyha3.json
Loading submodule modules/contraceptives/implant_contraceptive.json
Loading submodule modules/heart/chf_meds_hfref_nyha4.json
Loading submodule modules/breast_cancer/hormonetherapy_breast.json
Loading submodule modules/heart/chf_meds_hfref_nyha2.json
Loading submodule modules/contraceptives/injectable_contraceptive.json
Loading submodule modules/covid19/measurements_daily.json
Loading submodule modules/covid19/treat_blood_clot.json
Loading submodule modules/medications/hypertension_medication.json
Loading submodule modules/allergies/outgrow_food_allergies.json
Loading submodule modules/breast_cancer/chemotherapy_breast.json
Loading module modules/opioid_addiction.json
Loading module modules/cerebral_palsy.json
Loading module modules/dialysis.json
Loading module modules/allergic_rhinitis.json
Loading module modules/pregnancy.json
Loading module modules/atopy.json
Loading module modules/self_harm.json
Loading module modules/asthma.json
Loading module modules/covid19.json
Loading Lookup Table: covid19_prob.csv
Loading module modules/ear_infections.json
Loading module modules/sinusitis.json
Loading module modules/dementia.json
Loading module modules/veteran_hyperlipidemia.json
Loading module modules/mTBI.json
Loading module modules/veteran_prostate_cancer.json
Loading module modules/anemia___unknown_etiology.json
Loading module modules/urinary_tract_infections.json
Loading module modules/hypothyroidism.json
Loading module modules/osteoarthritis.json
Loading module modules/appendicitis.json
Loading module modules/copd.json
Loading module modules/contraceptive_maintenance.json
Loading module modules/fibromyalgia.json
Loading module modules/veteran_substance_abuse_treatment.json
Loading module modules/veteran_lung_cancer.json
Loading module modules/total_joint_replacement.json
Loading module modules/prescribing_opioids_for_chronic_pain_and_treatment_of_oud.json
Loading module modules/rheumatoid_arthritis.json
Loading module modules/sore_throat.json
Loading module modules/gallstones.json
Loading module modules/bronchitis.json
Loading module modules/spina_bifida.json
Loading module modules/sexual_activity.json
Loading module modules/homelessness.json
Loading module modules/epilepsy.json
Loading module modules/wellness_encounters.json
Loading module modules/injuries.json
Loading module modules/colorectal_cancer.json
Loading module modules/med_rec.json
Loading module modules/congestive_heart_failure.json
Loading module modules/veteran_self_harm.json
Loading module modules/veteran_mdd.json
Loading module modules/lung_cancer.json
Loading module modules/contraceptives.json
Loading module modules/osteoporosis.json
Loading module modules/breast_cancer.json
Loading module modules/female_reproduction.json
Loading module modules/veteran.json
Loading module modules/gout.json
Loading module modules/metabolic_syndrome_disease.json
Loading module modules/sepsis.json
Loading module modules/allergies.json
Loading module modules/metabolic_syndrome_care.json
Loading module modules/lupus.json
Loading module modules/cystic_fibrosis.json
Loading module modules/attention_deficit_disorder.json
Loading module modules/dermatitis.json
Loading module modules/food_allergies.json
Loading module modules/mend_program.json
Loading module modules/veteran_ptsd.json
Loading module modules/veteran_substance_abuse_conditions.json
Loading module modules/hypertension.json
Running with options:
Population: 100
Seed: 1625911868501
Provider Seed:1625911868501
Reference Time: 1625911868501
Location: Massachusetts
Min Age: 0
Max Age: 140
1 -- Patricio639 Fonseca493 (3 y/o M) North Andover, Massachusetts 
8 -- Foster87 Heidenreich818 (9 y/o M) Norwood, Massachusetts 
6 -- Isela504 Rempel203 (19 y/o F) Boxborough, Massachusetts 
2 -- Rosario163 Medhurst46 (24 y/o M) Boston, Massachusetts 
3 -- Boyd728 Hackett68 (45 y/o M) Barnstable, Massachusetts 
7 -- Zena758 Powlowski563 (49 y/o F) New Bedford, Massachusetts 
4 -- Yang129 Rosenbaum794 (63 y/o F) Springfield, Massachusetts 
5 -- Reena181 Reilly981 (60 y/o F) Acushnet, Massachusetts 
12 -- Ernesto186 Villareal516 (18 y/o M) Swampscott, Massachusetts 
11 -- Sudie246 Bechtelar572 (26 y/o F) Boston, Massachusetts 
10 -- Keven605 Shanahan202 (49 y/o M) Chelsea, Massachusetts 
13 -- Delicia67 Mueller846 (33 y/o F) Quincy, Massachusetts 
9 -- Denisha680 Kassulke119 (53 y/o F) Barnstable, Massachusetts 
14 -- Gabriel934 Granado100 (58 y/o M) Sandwich, Massachusetts DECEASED
16 -- Pierre431 Nikolaus26 (66 y/o M) Worcester, Massachusetts 
21 -- Berenice603 Marvin195 (9 y/o F) Foxborough, Massachusetts 
18 -- Rafaela464 Zboncak558 (45 y/o F) Concord, Massachusetts 
20 -- Ruben688 Medhurst46 (29 y/o M) East Sandwich, Massachusetts 
22 -- Dusty207 Bernier607 (11 y/o M) Nantucket, Massachusetts 
15 -- Roselia779 Thompson596 (91 y/o F) Easton, Massachusetts DECEASED
25 -- Chere867 Daniel959 (4 y/o F) Hudson, Massachusetts 
24 -- Brent147 Harris789 (10 y/o M) Dracut, Massachusetts 
23 -- Raymundo71 Labadie908 (10 y/o M) Abington, Massachusetts 
19 -- Rafael239 Franecki195 (57 y/o M) Weymouth, Massachusetts 
17 -- Chi716 Cormier289 (91 y/o F) East Douglas, Massachusetts DECEASED
14 -- Rodrigo242 Rodr?quez611 (54 y/o M) Sandwich, Massachusetts DECEASED
29 -- Ned189 Mills423 (13 y/o M) Marlborough, Massachusetts 
28 -- Trinidad33 Sauer652 (26 y/o M) Williamstown, Massachusetts 
26 -- Mikel238 Baumbach677 (54 y/o M) Topsfield, Massachusetts 
30 -- Mose244 Gislason620 (34 y/o M) Dracut, Massachusetts 
27 -- Britteny287 Pagac496 (44 y/o F) Billerica, Massachusetts 
32 -- Rudy520 Abshire638 (10 y/o M) Arlington, Massachusetts 
17 -- Camila223 Ankunding277 (20 y/o F) East Douglas, Massachusetts DECEASED
15 -- Nathalie366 Mitchell808 (85 y/o F) Easton, Massachusetts DECEASED
31 -- Adriana394 Preciado125 (55 y/o F) Worcester, Massachusetts 
33 -- Leila837 Smitham825 (48 y/o F) Springfield, Massachusetts 
35 -- Taylor21 Stiedemann542 (35 y/o M) Brimfield, Massachusetts 
38 -- Valerie954 Walker122 (5 y/o F) Peabody, Massachusetts 
34 -- Rosario163 Fadel536 (60 y/o M) Shrewsbury, Massachusetts 
36 -- Elsie248 Witting912 (16 y/o F) Adams, Massachusetts 
37 -- Chang901 Stroman228 (18 y/o F) Everett, Massachusetts 
41 -- Marco578 Yundt842 (16 y/o M) Walpole, Massachusetts 
17 -- Janis361 Gutmann970 (81 y/o F) East Douglas, Massachusetts DECEASED
43 -- Donnie175 Swaniawski813 (26 y/o F) Springfield, Massachusetts 
40 -- Lane844 Rath779 (42 y/o M) Charlton, Massachusetts 
39 -- Numbers230 Barton704 (52 y/o M) Taunton, Massachusetts 
14 -- Armando772 Cort?s936 (60 y/o M) Sandwich, Massachusetts 
42 -- Kassandra256 Wiegand701 (41 y/o F) Chelsea, Massachusetts 
44 -- Lucas404 Le?n820 (52 y/o M) Medford, Massachusetts 
49 -- Bok974 Rau926 (8 y/o F) Stoughton, Massachusetts 
48 -- Vennie613 Bogan287 (15 y/o F) Norwood, Massachusetts 
15 -- Charisse42 Parisian75 (102 y/o F) Easton, Massachusetts 
47 -- Annabelle638 Spencer878 (64 y/o F) Marlborough, Massachusetts 
17 -- Loida499 Rosenbaum794 (89 y/o F) East Douglas, Massachusetts DECEASED
52 -- Zana914 Gusikowski974 (41 y/o F) Hampden, Massachusetts 
46 -- Stacia109 Schowalter414 (68 y/o F) Auburn, Massachusetts 
45 -- Christian753 Bruen238 (73 y/o F) Pocasset, Massachusetts 
50 -- Quintin944 Willms744 (55 y/o M) Norwood, Massachusetts 
54 -- Aida517 Wiza601 (4 y/o F) Taunton, Massachusetts 
53 -- Sybil495 Braun514 (22 y/o F) Upton, Massachusetts 
51 -- Kristie55 Treutel973 (69 y/o F) Worcester, Massachusetts DECEASED
57 -- Glen190 Zemlak964 (13 y/o M) Lawrence, Massachusetts 
55 -- Mindi87 Homenick806 (65 y/o F) Acton, Massachusetts 
56 -- Jacinto644 Bauch723 (48 y/o M) Worcester, Massachusetts 
59 -- Arielle168 Robel940 (22 y/o F) Harvard, Massachusetts 
61 -- Alva958 Ryan260 (20 y/o M) Medford, Massachusetts 
60 -- Mathew182 Kuphal363 (43 y/o M) Somerville, Massachusetts 
58 -- August363 Wiza601 (52 y/o M) Methuen, Massachusetts 
62 -- Mel236 Mayer370 (21 y/o M) Newton, Massachusetts 
17 -- Ginny287 Streich926 (83 y/o F) East Douglas, Massachusetts DECEASED
64 -- Dexter530 Shields502 (22 y/o M) Ludlow, Massachusetts DECEASED
66 -- Zandra428 O'Hara248 (0 y/o F) Amherst, Massachusetts 
63 -- Tyron580 Abbott774 (67 y/o M) Lexington, Massachusetts DECEASED
68 -- Florine959 Wolf938 (10 y/o F) Hingham, Massachusetts 
67 -- Gerry91 Powlowski563 (35 y/o M) Worcester, Massachusetts 
64 -- Ollie731 Funk324 (53 y/o M) Ludlow, Massachusetts 
65 -- Rose199 Wehner319 (81 y/o F) Greenfield, Massachusetts 
17 -- Onita767 Conn188 (44 y/o F) East Douglas, Massachusetts DECEASED
72 -- Kyle55 Hilll811 (23 y/o M) Somerville, Massachusetts DECEASED
70 -- Delinda651 Casper496 (59 y/o F) Winchester, Massachusetts 
69 -- Lavera253 Langworth352 (77 y/o F) Haverhill, Massachusetts 
73 -- Ayesha583 Hauck852 (36 y/o F) Newton, Massachusetts 
63 -- Quincy153 Kohler843 (73 y/o M) Lexington, Massachusetts DECEASED
51 -- Louis204 Little434 (76 y/o F) Worcester, Massachusetts 
71 -- Lurlene215 Torp761 (71 y/o F) Plymouth, Massachusetts 
77 -- Soledad678 Guzm?n14 (5 y/o F) Winchendon, Massachusetts 
75 -- Margarita164 Legros616 (32 y/o F) Boston, Massachusetts 
76 -- Corey514 Balistreri607 (28 y/o M) Boston, Massachusetts 
72 -- Robt687 Morissette863 (69 y/o M) Somerville, Massachusetts 
74 -- Darrel772 Davis923 (59 y/o M) Grafton, Massachusetts 
17 -- Luna60 Will178 (92 y/o F) East Douglas, Massachusetts 
79 -- Mercy752 Ankunding277 (49 y/o F) Taunton, Massachusetts 
84 -- Gary33 Boehm581 (3 y/o F) Boston, Massachusetts 
80 -- Roscoe437 Windler79 (45 y/o M) Marlborough, Massachusetts 
78 -- Dexter530 Price929 (73 y/o M) Waltham, Massachusetts DECEASED
63 -- Eliseo499 Wyman904 (75 y/o M) Lexington, Massachusetts 
85 -- Yuriko393 Jakubowski832 (10 y/o F) Lancaster, Massachusetts 
82 -- Ka422 Ryan260 (56 y/o F) Plymouth, Massachusetts 
83 -- Raymon366 Sauer652 (48 y/o M) Cambridge, Massachusetts 
81 -- Karren465 Little434 (79 y/o F) Rehoboth, Massachusetts 
86 -- Gwyneth692 Will178 (49 y/o F) Worcester, Massachusetts 
88 -- Alphonso102 Collins926 (26 y/o M) Boston, Massachusetts 
89 -- Neal874 Kovacek682 (36 y/o M) Worcester, Massachusetts 
87 -- Kris249 Welch179 (58 y/o M) Plymouth, Massachusetts 
90 -- Stevie682 Russel238 (52 y/o F) Framingham, Massachusetts 
94 -- Maximo817 Bartell116 (30 y/o M) Milford, Massachusetts 
95 -- Lupe126 Gulgowski816 (26 y/o M) Brockton, Massachusetts 
92 -- Maricela194 Grant908 (61 y/o F) Boston, Massachusetts 
91 -- Karren465 Stoltenberg489 (73 y/o F) Boston, Massachusetts 
93 -- Elinor7 Wilderman619 (48 y/o F) Worcester, Massachusetts DECEASED
78 -- Jayson808 Johnston597 (74 y/o M) Waltham, Massachusetts 
97 -- Garfield38 Kessler503 (51 y/o M) Attleboro, Massachusetts DECEASED
96 -- Sarina640 Kilback373 (39 y/o F) Fall River, Massachusetts 
99 -- Tula326 Gulgowski816 (24 y/o F) Watertown, Massachusetts 
98 -- Demarcus108 Rosenbaum794 (62 y/o M) Newton, Massachusetts DECEASED
100 -- Joelle163 Feeney44 (46 y/o F) Rehoboth, Massachusetts DECEASED
97 -- Carmine137 Harber290 (62 y/o M) Attleboro, Massachusetts 
100 -- Loree183 Keebler762 (47 y/o F) Rehoboth, Massachusetts 
98 -- Billy698 Homenick806 (78 y/o M) Newton, Massachusetts 
93 -- Karine844 Blanda868 (58 y/o F) Worcester, Massachusetts 
Records: total=120, alive=100, dead=20
```