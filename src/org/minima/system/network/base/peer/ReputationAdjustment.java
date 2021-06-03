/*
 * Copyright 2020 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.minima.system.network.base.peer;

public enum ReputationAdjustment {

  // from ReputationManager.java
  //static final int LARGE_CHANGE = 10;
  //static final int SMALL_CHANGE = 3;
  LARGE_PENALTY(-10),
  SMALL_PENALTY(-3),
  SMALL_REWARD(3),
  LARGE_REWARD(10);

  private final int scoreChange;

  ReputationAdjustment(final int scoreChange) {
    this.scoreChange = scoreChange;
  }

  int getScoreChange() {
    return scoreChange;
  }
}