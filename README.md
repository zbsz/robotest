# RoboTest
RoboTest lets you use [Robolectric](http://www.robolectric.org) library in tests using [ScalaTest](http://www.scalatest.org) framework, in Scala project.
It provides `RobolectricSuite` mixin which has similar functionality as `RobolectricTestRunner` in regular JUnit tests.

## Using RoboTest
Latest RoboTest version supports Robolectric 3.0-SNAPSHOT 

Robolectric 2.4 is supported by Robotest 0.7 (see releases).

### SBT Configuration
For working sample, check example project included in Robotest sources.

```
resolvers += Seq(
  "RoboTest releases" at "https://raw.github.com/zbsz/mvn-repo/master/releases/",
  Resolver.sonatypeRepo("snapshots")
)

libraryDependencies ++= Seq(
  "com.geteit" %% "robotest" % "0.11" % Test,                              // latest RoboTest version
  "org.scalatest" %% "scalatest" % "2.2.5" % Test 
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
    helper = new SQLiteOpenHelper(RuntimeEnvironment.application, "test", null, 1) {
      override def onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int): Unit = {}
      override def onCreate(db: SQLiteDatabase): Unit = {}
    }
  }

  override protected def afterAll(): Unit = {
    RuntimeEnvironment.application.getDatabasePath(helper.getDatabaseName).delete()
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

Checkout example app project for complete setup and some other tests, also using AndroidManifest and resources.

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
