# Diet Quality: normal Coach conversation handover

The Coach owns assessments; Android displays them. Continue photo/text food
logging through ChatGPT, saving food in `Health` as before. After a log or review,
read back the food actually saved and assess the selected Perth date. A chat
response alone is not a saved assessment. This does not run in the background.

## Scoring rubric dq-v1

This is an estimated coaching score, not a validated clinical scale or food-label
Health Star Rating. Assess diet quality separately from energy intake, weight
loss, exercise calories and target adherence. Never reward eating too little.
Mention suspected under-fuelling separately; leave approved targets unchanged.

Use five bands per component, with these exact point mappings:

| Component | Band 0 | Band 1 | Band 2 | Band 3 | Band 4 |
|---|---:|---:|---:|---:|---:|
| Fruit/vegetables | 0 | 8 | 15 | 23 | 30 |
| Fibre-rich foods | 0 | 5 | 10 | 15 | 20 |
| Protein/calcium sources | 0 | 5 | 10 | 15 | 20 |
| Fat quality | 0 | 4 | 8 | 11 | 15 |
| Discretionary-food balance | 0 | 4 | 8 | 11 | 15 |

Judge the balance of food logged **so far** for a provisional assessment. Do not
subtract points merely because lunch/dinner have not happened. Use the same
bands after a completed-day review, considering the full day's reported intake.

- **Fruit/vegetables:** 0 none in the reported intake; 1 incidental amounts;
  2 meaningful produce but limited coverage/variety; 3 substantial produce across
  most logged main meals/snacks; 4 substantial, varied produce well represented
  across the reported eating occasions. A fruit-rich breakfast can score well
  before dinner exists. Never infer an unlogged meal was eaten or skipped.
- **Fibre-rich foods:** 0 no meaningful sources; 1 mostly refined foods with a
  small source; 2 some wholegrain/legume/nut/seed/produce sources; 3 these sources
  predominate over refined equivalents; 4 consistent, varied fibre-rich choices.
- **Protein/calcium:** 0 neither meaningfully represented; 1 sparse/one-sided;
  2 one well represented and the other limited; 3 both represented with suitable
  variety; 4 both consistently well represented across the eating occasions.
  Do not treat extra protein above needs as extra points.
- **Fat quality:** 0 known saturated-fat-rich choices dominate; 1 mostly those
  choices; 2 mixed balance; 3 mostly unsaturated sources; 4 consistently favourable
  balance. Oils, nuts, seeds, avocado and fish are examples of unsaturated sources.
  Processing alone is not a penalty: yoghurt and frozen vegetables can fit well.
- **Discretionary balance:** 0 discretionary choices dominate food intake;
  1 a large share; 2 a moderate share; 3 a small share alongside nutritious foods;
  4 minimal discretionary intake with nutritious foods forming the main pattern.
  A treat does not fail the day. Consider gels, sports drinks and similar foods
  used during a long run in their fuelling context; count their calories normally,
  but do not automatically penalise them as everyday discretionary snacking.

These are qualitative anchors, not measured nutrient thresholds. Explain each
band in <=500 characters. Do not invent fibre, salt, fat-type or sugar grams
from photos. Where an ingredient is unknown, say so. If a whole component cannot
be judged, use its midpoint provisionally, explicitly label it uncertain in its
reason and set overall confidence low. With fewer than three assessable
components, withhold the score and ask for the most useful missing detail.
Confidence high requires clear foods/portions and relevant ingredient information;
medium allows reasonable portion estimates; low flags substantial uncertainty.
Logging completeness and confidence are different from nutritional quality.

Source principles: Australian Dietary Guidelines,
https://www.eatforhealth.gov.au/guidelines/australian-dietary-guidelines-1-5.
The weights and band mappings above are our own consistent coaching convention.

## Worksheet and revision contract

Use `DietQuality` in the existing training workbook. Discover and verify the
workbook through the same connection used for `Health`/`NutritionTargets`.
Do not copy private food data or credentials into GitHub.

Exact A:W headers:

```text
assessment_id,revision,date,status,score,fruit_veg_points,fibre_points,protein_calcium_points,fat_quality_points,discretionary_points,confidence,summary,strength,improvement,fruit_veg_reason,fibre_reason,protein_calcium_reason,fat_quality_reason,discretionary_reason,food_change_ids,source,assessed_at,rubric_version
```

1. Read metadata and bounded `Health` and `DietQuality` rectangles including their
   headers. Page within actual grid bounds when necessary. Use current `Health`
   per record_id: latest parsed updated_at, then lexically greatest change_id.
   Take only non-deleted `kind=meal` on the selected date. Exclude favourites,
   weights, superseded rows and other days. Respect portion multipliers when
   assessing foods. If no active meals exist, withhold a numeric assessment.
2. `food_change_ids`: sort the selected active meal change_ids in ordinary ASCII
   lexical order and join with `|`, with no spaces. Maximum 100 IDs, each 1–100
   characters in `[a-zA-Z0-9_:.\-]`. This fingerprints exactly the food assessed.
   A later food edit/deletion/move will mark the score as needing review in the
   app. Never fingerprint a favourite or a superseded food revision.
3. Stable ID `diet-quality-YYYY-MM-DD`; integer revision is one above the greatest
   existing revision for that ID, including withdrawn revisions. Append a full row
   to a freshly read blank row; never overwrite history. Avoid concurrent writers.
4. `status=provisional` during the day or when logging completeness is uncertain.
   Use `complete` only after the user confirms the full day is logged or an
   explicit end-of-day review establishes completeness. Midnight does not
   automatically finalise anything. Corrected past days may be reassessed.
5. `score` is the exact integer sum of the five points. Confidence is
   low/medium/high. Supply summary (<=1000 characters), strength and one practical
   improvement (<=500 each), and five reasons (<=500 each). Source `ChatGPT Coach`
   (<=120). `assessed_at` is the actual current UTC timestamp with milliseconds,
   `YYYY-MM-DDTHH:mm:ss.SSSZ`; `rubric_version=dq-v1`. Dates are Perth YYYY-MM-DD.
6. Write strings as RAW/literal string cells, points/revision/score as numbers.
   Re-read immediately before appending and read back the exact row afterwards.
   Confirm values, unique ID+revision, correct total and signature against a fresh
   food read. If food changed during the write, reassess with a new revision.
   Report the score, provisional/complete status and saved date only after the
   readback succeeds. If tools cannot write, explicitly say it was not saved.
7. Identical ID+revision+payload retries are harmless. Different payloads for the
   same ID+revision block import: do not delete history or silently choose one.
   Resolve the conflicting erroneous row deliberately. `withdrawn` on a higher
   revision hides an incorrect score; retain otherwise valid assessment fields.

No targets, food records, weights or training plans are changed by this procedure.
App sync imports the saved assessment; it does not compute or request a new one.
Manual phone entries/edits need a subsequent Coach assessment. No backfill of
scores is implied by installing the feature. Weekly averages include only complete
assessments whose meal signatures still match, with the contributing day count.

## Activation

Add the new empty worksheet and headers. Deploy the updated `backend/Code.gs` to
the **existing** Apps Script web app (new version of that deployment), preserving
its URL, execute-as identity, access settings and Script Properties. Do not create
a second web app or change secrets. Install the new APK and refresh. Until bridge
deployment, ordinary sync remains usable; diet quality reports unavailable.
