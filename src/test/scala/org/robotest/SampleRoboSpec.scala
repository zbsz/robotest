package org.robotest

import android.database.sqlite.{SQLiteDatabase, SQLiteOpenHelper}
import org.robolectric.Robolectric
import org.scalatest.{FeatureSpec, Matchers, RobolectricTests}

/**
  */
class SampleRoboSpec extends FeatureSpec with Matchers with RobolectricTests {

  feature("Robolectric test using database") {

    scenario("Create and close database") {

      Robolectric.application should not be null

      val helper = new SQLiteOpenHelper(Robolectric.application, "test", null, 1) {
        override def onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int): Unit = {}
        override def onCreate(db: SQLiteDatabase): Unit = {}
      }

      val db = helper.getWritableDatabase
      db should not be null
      db.close()
    }

    scenario("Create and close database again") {

      Robolectric.application should not be null

      val helper = new SQLiteOpenHelper(Robolectric.application, "test", null, 1) {
        override def onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int): Unit = {}
        override def onCreate(db: SQLiteDatabase): Unit = {}
      }

      val db = helper.getWritableDatabase
      db should not be null
      db.close()
    }
  }
}
