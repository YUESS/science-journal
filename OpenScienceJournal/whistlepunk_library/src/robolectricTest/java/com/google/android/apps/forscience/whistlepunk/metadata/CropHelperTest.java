/*
 *  Copyright 2016 Google Inc. All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.google.android.apps.forscience.whistlepunk.metadata;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.android.apps.forscience.javalib.Success;
import com.google.android.apps.forscience.whistlepunk.DataControllerImpl;
import com.google.android.apps.forscience.whistlepunk.ExperimentCreator;
import com.google.android.apps.forscience.whistlepunk.TestConsumers;
import com.google.android.apps.forscience.whistlepunk.data.nano.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Trial;
import com.google.android.apps.forscience.whistlepunk.filemetadata.TrialStats;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTrial.Range;
import com.google.android.apps.forscience.whistlepunk.metadata.nano.GoosciTrial;
import com.google.android.apps.forscience.whistlepunk.sensordb.InMemorySensorDatabase;
import com.google.android.apps.forscience.whistlepunk.sensordb.MemoryMetadataManager;
import com.google.android.apps.forscience.whistlepunk.sensordb.StoringConsumer;
import com.google.common.util.concurrent.MoreExecutors;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Tests for {@link CropHelper} */
@RunWith(RobolectricTestRunner.class)
public class CropHelperTest {
  private DataControllerImpl dataController;
  private MemoryMetadataManager metadataManager;
  private CropHelper.CropTrialListener cropTrialListener;
  private boolean cropCompleted = false;
  private boolean cropFailed = false;
  private GoosciSensorLayout.SensorLayout[] sensorLayouts;
  private final double DELTA = 0.01;

  @Before
  public void setUp() {
    metadataManager = new MemoryMetadataManager();
    dataController = new InMemorySensorDatabase().makeSimpleController(metadataManager);
    sensorLayouts = new GoosciSensorLayout.SensorLayout[1];
    GoosciSensorLayout.SensorLayout layout = new GoosciSensorLayout.SensorLayout();
    layout.sensorId = "sensor";
    sensorLayouts[0] = layout;
    resetCropRunListener();
  }

  private void resetCropRunListener() {
    cropCompleted = false;
    cropFailed = false;
    cropTrialListener =
        new CropHelper.CropTrialListener() {

          @Override
          public void onCropCompleted() {
            cropCompleted = true;
          }

          @Override
          public void onCropFailed(int errorId) {
            cropFailed = true;
          }
        };
  }

  // Adds stats to the MemoryMetadataManager for any test that has a getStats and expects
  // there to exist stats for this sensor.
  // In a real trial, stats are set when recording stops, so any crop should have some sensor
  // stats already set and this kind of thing is unnecessary. But we don't need to set legit stats
  // because we aren't checking the "pre-crop" values, so empty stats are fine here.
  private void setEmptyStats(Experiment experiment, String trialId) {
    experiment.getTrial(trialId).setStats(new TrialStats("sensor"));
    metadataManager.updateExperiment(experiment, true);
  }

  private Trial makeCommonTrial() {
    GoosciTrial.Trial trialProto = new GoosciTrial.Trial();
    trialProto.trialId = "0";
    trialProto.sensorLayouts = sensorLayouts;
    trialProto.recordingRange = Range.newBuilder().setStartMs(0).setEndMs(2000).build();
    return Trial.fromTrial(trialProto);
  }

  @Test
  public void testCropRun_failsOutsideBounds() {
    GoosciTrial.Trial trialProto = new GoosciTrial.Trial();
    trialProto.trialId = "runId";
    trialProto.creationTimeMs = 42;
    trialProto.recordingRange = Range.newBuilder().setStartMs(0).setEndMs(10).build();
    Trial trial = Trial.fromTrial(trialProto);

    Experiment experiment = ExperimentCreator.newExperimentForTesting(10, "experimentId", 0);
    experiment.addTrial(trial);

    CropHelper cropHelper = new CropHelper(MoreExecutors.directExecutor(), dataController);
    cropHelper.cropTrial(null, experiment, "runId", -1, 10, cropTrialListener);
    assertTrue(cropFailed);
    assertFalse(cropCompleted);

    resetCropRunListener();
    cropHelper.cropTrial(null, experiment, "runId", 1, 20, cropTrialListener);
    assertTrue(cropFailed);
    assertFalse(cropCompleted);
  }

  @Test
  public void testCropRun_alreadyCroppedRun() {
    StoringConsumer<Experiment> cExperiment = new StoringConsumer<>();
    dataController.createExperiment(cExperiment);
    Experiment experiment = cExperiment.getValue();
    Trial trial = makeCommonTrial();
    Range cropRange = Range.newBuilder().setStartMs(2).setEndMs(1008).build();
    trial.setCropRange(cropRange);
    experiment.addTrial(trial);
    dataController.updateExperiment(
        experiment.getExperimentId(), TestConsumers.<Success>expectingSuccess());

    dataController.addScalarReading(trial.getTrialId(), "sensor", 0, 50, 50);
    setEmptyStats(experiment, trial.getTrialId());

    CropHelper cropHelper = new CropHelper(MoreExecutors.directExecutor(), dataController);
    cropHelper.cropTrial(null, experiment, trial.getTrialId(), 4, 1006, cropTrialListener);
    assertTrue(cropCompleted);
    assertEquals(trial.getFirstTimestamp(), 4);
    assertEquals(trial.getLastTimestamp(), 1006);
    assertEquals(trial.getOriginalFirstTimestamp(), 0);
    assertEquals(trial.getOriginalLastTimestamp(), 2000);
    assertTrue(
        metadataManager
            .getExperimentById(experiment.getExperimentId())
            .getTrial(trial.getTrialId())
            .getStatsForSensor("sensor")
            .statsAreValid());
  }

  @Test
  public void testCropRun_onUncroppedRun() {
    StoringConsumer<Experiment> cExperiment = new StoringConsumer<>();
    dataController.createExperiment(cExperiment);
    Experiment experiment = cExperiment.getValue();
    Trial trial = makeCommonTrial();
    experiment.addTrial(trial);
    dataController.updateExperiment(
        experiment.getExperimentId(), TestConsumers.<Success>expectingSuccess());

    dataController.addScalarReading(trial.getTrialId(), "sensor", 0, 1, 1); // This gets cropped out
    dataController.addScalarReading(trial.getTrialId(), "sensor", 0, 50, 50);
    dataController.addScalarReading(trial.getTrialId(), "sensor", 0, 60, 60);
    dataController.addScalarReading(trial.getTrialId(), "sensor", 0, 70, 70);
    setEmptyStats(experiment, trial.getTrialId());
    CropHelper cropHelper = new CropHelper(MoreExecutors.directExecutor(), dataController);
    cropHelper.cropTrial(null, experiment, trial.getTrialId(), 2, 1008, cropTrialListener);
    assertTrue(cropCompleted);
    assertEquals(trial.getFirstTimestamp(), 2);
    assertEquals(trial.getLastTimestamp(), 1008);
    assertEquals(trial.getOriginalFirstTimestamp(), 0);
    assertEquals(trial.getOriginalLastTimestamp(), 2000);

    TrialStats stats =
        metadataManager
            .getExperimentById(experiment.getExperimentId())
            .getTrial(trial.getTrialId())
            .getStatsForSensor("sensor");
    assertTrue(stats.statsAreValid());
    assertEquals(stats.getStatValue(GoosciTrial.SensorStat.StatType.MINIMUM, -1), 50.0, DELTA);
    assertEquals(stats.getStatValue(GoosciTrial.SensorStat.StatType.AVERAGE, -1), 60.0, DELTA);
    assertEquals(stats.getStatValue(GoosciTrial.SensorStat.StatType.MAXIMUM, -1), 70.0, DELTA);
    assertEquals(
        stats.getStatValue(GoosciTrial.SensorStat.StatType.NUM_DATA_POINTS, -1), 3.0, DELTA);
  }

  @Test
  public void testCropRun_sensorWithNoDataStatsInvalid() {
    StoringConsumer<Experiment> cExperiment = new StoringConsumer<>();
    dataController.createExperiment(cExperiment);
    Experiment experiment = cExperiment.getValue();
    final Trial trial = makeCommonTrial();
    experiment.addTrial(trial);
    dataController.updateExperiment(
        experiment.getExperimentId(), TestConsumers.<Success>expectingSuccess());
    setEmptyStats(experiment, trial.getTrialId());
    CropHelper cropHelper = new CropHelper(MoreExecutors.directExecutor(), dataController);
    cropHelper.cropTrial(null, experiment, trial.getTrialId(), 2, 1008, cropTrialListener);
    assertTrue(cropCompleted);
    assertFalse(
        metadataManager
            .getExperimentById(experiment.getExperimentId())
            .getTrial(trial.getTrialId())
            .getStatsForSensor("sensor")
            .statsAreValid());
  }
}
