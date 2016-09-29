/*
 * Copyright 2016 Red Bull Media House GmbH <http://www.redbullmediahouse.com> - all rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.rbmhtechnology.eventuate.tools.logviewer

import com.rbmhtechnology.eventuate.tools.logviewer.LogViewerParameters.CaseClass
import com.rbmhtechnology.eventuate.tools.logviewer.LogViewerParameters.Velocity
import com.rbmhtechnology.eventuate.tools.logviewer.LogViewerParameters.parseCommandLineArgs
import org.scalatest.EitherValues
import org.scalatest.Inside
import org.scalatest.Matchers
import org.scalatest.WordSpec

class LogViewerParameterSpec extends WordSpec with Matchers with EitherValues with Inside {

  "LogViewerParameter.parseCommandLineArgs" when {
    "invoked with valid formatter" must {
      "return valid LogViewerParameters" in {
        parseCommandLineArgs("", Array("--eventFormatter", "caseCLASS")).right.value.eventFormatter shouldBe CaseClass
        parseCommandLineArgs("", Array("--eventFormatter", "VELOcity")).right.value.eventFormatter shouldBe Velocity
      }
    }
    "invoked with an invalid formatter" must {
      "return an usage string and return code of 1" in {
        inside(parseCommandLineArgs("", Array("--eventFormatter", "xyz")).left.value) {
          case (usage, returnCode) =>
            usage should include("'--eventFormatter' must be one of")
            returnCode shouldBe 1
        }
      }
    }
  }
}
