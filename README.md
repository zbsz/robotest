# RoboTest
RoboTest lets you use [Robolectric 2](http://www.robolectric.org) library in tests using [ScalaTest](http://www.scalatest.org) framework, in Scala project.
It provides `RobolectricSuite` mixin which has similar functionality as `RobolectricTestRunner` in regular JUnit tests.

## Using RoboTest
Latest RoboTest version supports only Robolectric 2.3.

### Robolectric dependencies

Make sure to have required robolectric libraries installed in local maven repository. Here is relevant part of robolectric eradme file:

Robolectric requires the Google APIs for Android (specifically, the maps JAR) and Android support-v4 library. To download this onto your development
machine use the Android SDK tools and then run the following to install them to your local Maven repository:

```
mvn install:install-file -DgroupId=com.google.android.maps \
  -DartifactId=maps \
  -Dversion=18_r3 \
  -Dpackaging=jar \
  -Dfile="$ANDROID_HOME/add-ons/addon-google_apis-google-18/libs/maps.jar"

mvn install:install-file -DgroupId=com.android.support \
  -DartifactId=support-v4 \
  -Dversion=19.0.1 \
  -Dpackaging=jar \
  -Dfile="$ANDROID_HOME/extras/android/support/v4/android-support-v4.jar"
```

You will need to either replace or have `ANDROID_HOME` set to your local Android SDK for Maven to be able to install the jar.

### SBT Configuration
```
resolvers += "Local Maven Repository" at "file://"+Path.userHome.absolutePath+"/.m2/repository"

resolvers += "RoboTest releases" at "https://raw.github.com/zbsz/mvn-repo/master/releases/"

libraryDependencies ++= Seq(
  "org.robolectric" % "android-all" % "4.3_r2-robolectric-0" % "provided",  // android version used by Robolectric 2.3
  "junit" % "junit" % "4.8.2" % "test",                                     // required by Robolectric 2.3
  "com.geteit" %% "robotest" % "0.4" % "test",                              // latest RoboTest version 
  "org.scalatest" %% "scalatest" % "2.1.6" % "test"
)

fork in Test := true

javaOptions in Test ++= Seq("-XX:MaxPermSize=2048M", "-XX:+CMSClassUnloadingEnabled")   // usually needed due to using forked tests
```

#### RoboTest is not thread safe
Due to the way Robolectric and SBT use class loaders, tests should be executed in separate JVM using fork option:
```
fork in Test := true
```

If your tests don't use database then it's possible to run them in single JVM, but then all tests can not be run in parallel, which is the default in SBT, it's very important to disable parallel execution:
```
parallelExecution in Test := false
```

### Writing tests
To use Robolectric in your tests just add `RobolectricSuite` mixin to your test, make sure to add it as the last trait in class declaration.
```
class SampleRoboSpec extends FeatureSpec with RobolectricSuite {
  ...
}
```

It should be possible to use any testing style supported by ScalaTest, lifecycle traits like `BeforAndAfter` and `BeforeAndAfterAll` are also supported. 

Here is sample test using shared database for all tests:
```
class DatabaseRoboSpec extends FeatureSpec with Matchers with BeforeAndAfter with BeforeAndAfterAll with RobolectricSuite {

  var helper: SQLiteOpenHelper = _
  var db: SQLiteDatabase = _

  override protected def beforeAll(): Unit = {
    helper = new SQLiteOpenHelper(Robolectric.application, "test", null, 1) {
      override def onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int): Unit = {}
      override def onCreate(db: SQLiteDatabase): Unit = {}
    }
  }

  override protected def afterAll(): Unit = {
    Robolectric.application.getDatabasePath(helper.getDatabaseName).delete()
  }

  before {
    db = helper.getWritableDatabase
    db.execSQL("CREATE TABLE Test(_id INTEGER PRIMARY KEY, value TEXT);")
  }

  after {
    db.execSQL("DROP TABLE IF EXISTS Test;")
    db.close()
  }

  feature("Robolectric test using database") {

    scenario("Insert row") {
      val values = new ContentValues()
      values.put("_id", Integer.valueOf(1))
      values.put("value", "test")

      db.insert("Test", null, values) shouldEqual 1
    }

    scenario("Query all rows from empty table") {

      val cursor = db.query("Test", null, null, null, null, null, null)
      cursor.moveToFirst() shouldEqual false
      cursor.getCount shouldEqual 0
    }
  }
}
```

### Shared state
All tests in  `RobolectricSuite` are executed within single Robolectric environment, this is different from how default JUnit test runner works. This means that Robolectric state is not reset between individual test runs withing single suite, any changes to static variables, databases or properties done in one test will be present when subsequent test runs. Use `BeforeAndAfter` to perform any necessary cleanup.
This approach seems to better fit how ScalaTest tests are usually written, and allows us to full ScalaTest potential.

## Common problems
```
com.almworks.sqlite4java.SQLiteException: [-91] cannot load library: java.lang.UnsatisfiedLinkError: Native Library /private/var/folders/94/d32jgq813kl5wp2b9c6kdl180000gn/T/robolectric-libs/libsqlite4java.dylib already loaded in another classloader
```
This error happens on the second run of tests in single SBT session, it's a result of how class loaders are created by Robolectric and SBT. You need to run tests in separate JVM to prevent this problem, just use fork option in `build.sbt`:
```
fork in Test := true
```

## Limitations
Current RoboTest version doesn't use `AndroidManifest.xml` and doesn't really support app resources. So it's only usable for limited cases, mostly for libraries, and less for actual android applications. We use it succesfully to test [slickdroid](https://github.com/zbsz/slickdroid) project.

Full app support should be quite easy to add once needed, pull requests will be welcome.
