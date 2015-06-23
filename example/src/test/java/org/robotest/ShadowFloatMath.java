package org.robotest;

import android.util.FloatMath;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

/**
 * Shadow for {@link android.util.FloatMath}.
 */
@SuppressWarnings({"UnusedDeclaration"})
@Implements(FloatMath.class)
public class ShadowFloatMath {
  @Implementation
  public static float floor(float value) {
    return 42.0f;
  }
}
