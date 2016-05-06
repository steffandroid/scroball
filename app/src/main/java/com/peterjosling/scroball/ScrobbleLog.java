package com.peterjosling.scroball;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;

import com.peterjosling.scroball.ScrobbleLogContract.ScrobbleLogEntry;

import java.util.ArrayList;
import java.util.List;

public class ScrobbleLog {

  private static final int MAX_ROWS = 1000;

  private SQLiteDatabase db;
  private ScrobbleLogDbHelper dbHelper;

  public ScrobbleLog(Context context) {
    dbHelper = new ScrobbleLogDbHelper(context);
  }

  public void open() {
      db = dbHelper.getWritableDatabase();
  }

  public List<Scrobble> read() {
    String sortOrder = ScrobbleLogEntry.COLUMN_NAME_TIMESTAMP + " DESC";
    Cursor cursor = db.query(ScrobbleLogEntry.TABLE_NAME, null, null, null, null, null, sortOrder);
    List<Scrobble> scrobbles = readScrobblesFromCursor(cursor);
    cursor.close();
    return scrobbles;
  }

  public void write(Scrobble scrobble) {
    Track track = scrobble.track();
    ScrobbleStatus status = scrobble.status();
    ContentValues values = new ContentValues();
    values.put(ScrobbleLogEntry.COLUMN_NAME_TIMESTAMP, scrobble.timestamp());
    values.put(ScrobbleLogEntry.COLUMN_NAME_ARTIST, track.artist());
    values.put(ScrobbleLogEntry.COLUMN_NAME_TRACK, track.track());
    values.put(ScrobbleLogEntry.COLUMN_NAME_STATUS, scrobble.status().getErrorCode());

    if (track.album().isPresent()) {
      values.put(ScrobbleLogEntry.COLUMN_NAME_ALBUM, track.album().get());
    }

    if (status.getDbId() > -1) {
      String selection = ScrobbleLogEntry._ID + " LIKE ?";
      String[] selectionArgs = {String.valueOf(status.getDbId())};
      db.update(ScrobbleLogEntry.TABLE_NAME, values, selection, selectionArgs);
    } else {
      long id = db.insert(ScrobbleLogEntry.TABLE_NAME, "null", values);
      scrobble.status().setDbId(id);
    }
  }

  public void write(List<Scrobble> scrobbles) {
    for (Scrobble scrobble : scrobbles) {
      write(scrobble);
    }
  }

  public List<Scrobble> readPending() {
    String sortOrder = ScrobbleLogEntry.COLUMN_NAME_TIMESTAMP + " DESC";
    String[] cols = new String[]{ScrobbleLogEntry.COLUMN_NAME_STATUS};
    Cursor cursor = db.query(ScrobbleLogEntry.TABLE_NAME, null, "?=0", cols, null, null, sortOrder);
    List<Scrobble> pending = readScrobblesFromCursor(cursor);
    cursor.close();
    return pending;
  }

  public void prune() {
    String[] cols = new String[]{ScrobbleLogEntry.COLUMN_NAME_STATUS};
    long rowCount = DatabaseUtils.queryNumEntries(db, ScrobbleLogEntry.TABLE_NAME, "?>0", cols);
    long toRemove = MAX_ROWS - rowCount;

    if (toRemove > 0) {
      // TODO
    }
  }

  private List<Scrobble> readScrobblesFromCursor(Cursor cursor) {
    List<Scrobble> scrobbles = new ArrayList<>();

    int rows = cursor.getCount();
    cursor.moveToFirst();

    for (int i = 0; i < rows; i++) {
      long id = cursor.getLong(cursor.getColumnIndexOrThrow(ScrobbleLogEntry._ID));
      int timestamp = cursor.getInt(cursor.getColumnIndexOrThrow(ScrobbleLogEntry.COLUMN_NAME_TIMESTAMP));
      int status = cursor.getInt(cursor.getColumnIndexOrThrow(ScrobbleLogEntry.COLUMN_NAME_STATUS));
      String artist = cursor.getString(cursor.getColumnIndexOrThrow(ScrobbleLogEntry.COLUMN_NAME_ARTIST));
      String track = cursor.getString(cursor.getColumnIndexOrThrow(ScrobbleLogEntry.COLUMN_NAME_TRACK));
      String album = cursor.getString(cursor.getColumnIndexOrThrow(ScrobbleLogEntry.COLUMN_NAME_ALBUM));

      ScrobbleStatus statusObj = new ScrobbleStatus(status, id);

      Track trackObj = ImmutableTrack.builder()
          .artist(artist)
          .album(album)
          .track(track)
          .build();

      Scrobble scrobble = ImmutableScrobble.builder()
          .status(statusObj)
          .track(trackObj)
          .timestamp(timestamp)
          .build();

      scrobbles.add(scrobble);
      cursor.moveToNext();
    }

    return scrobbles;
  }
}
