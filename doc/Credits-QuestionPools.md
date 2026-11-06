# License Study question pools

The License Study program (radio computer, 1.12) ships three question pools in
`src/main/resources/data/radiocraft/license_pools/`:

| File | Pool | Valid | Questions | Exam |
|---|---|---|---|---|
| `technician.json` | Technician (Element 2) | July 1, 2026 to June 30, 2030 | 409 | 35 (26 to pass) |
| `general.json` | General (Element 3) | July 1, 2023 to June 30, 2027 | 423 | 35 (26 to pass) |
| `extra.json` | Amateur Extra (Element 4) | July 1, 2024 to June 30, 2028 | 599 | 50 (37 to pass) |

**Source.** The questions, answers and correct letters are the NCVEC Question Pool Committee's pools, released into
the public domain. They were taken in machine-readable form from Russ Olsen's
[ham_radio_question_pool](https://github.com/russolsen/ham_radio_question_pool) (Apache License 2.0 for its packaging
and programs) and converted by `tools/license_pools.py`
(`python -I tools/license_pools.py <clone of ham_radio_question_pool>`), which folds a few typographic characters
(curly quotes, dashes, the ohm and degree signs, Greek letters) to ASCII for the computer's text-mode font. The
subelement names are the NCVEC syllabus titles, written for this file.

**Figures.** The 45 questions that refer to a figure (T-1 to T-3, G7-1, E5-1, E6-1 to E6-3, E7-1 to E7-3, E9-1 to
E9-3) keep their `figure` field; the computer cannot draw the figures, so study and practice exams skip them. Every
group still has questions without a figure, so the exams keep their full size.

**Updating a pool.** Rerun the converter on a newer clone, or have server admins drop their own file in
`config/radiocraft/license_pools/` (same id replaces the shipped pool) or a data pack's
`data/<namespace>/license_pools/`. The format is described in the Handbook entry "License Study" and in the Javadoc of
`common/computer/license/LicensePool`.
