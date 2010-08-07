package org.jetbrains.plugins.scala.lang.resolve2

/**
 * @author Alexander Podkhalyuzin
 */

class Bug2Test extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "bug2/"
  }

  def testSCL2268 = doTest
}