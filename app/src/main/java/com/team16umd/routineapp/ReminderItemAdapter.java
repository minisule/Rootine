package com.team16umd.routineapp;

import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import com.facebook.internal.LockOnGetVariable;
import com.facebook.share.ShareApi;
import com.facebook.share.model.SharePhoto;
import com.facebook.share.model.SharePhotoContent;
import com.facebook.share.widget.ShareDialog;
import com.firebase.client.AuthData;
import com.firebase.client.DataSnapshot;
import com.firebase.client.Firebase;
import com.firebase.client.FirebaseError;
import com.firebase.client.MutableData;
import com.firebase.client.ServerValue;
import com.firebase.client.Transaction;
import com.firebase.client.ValueEventListener;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/**
 * Created by thekyei on 4/20/16.
 */

public class ReminderItemAdapter extends BaseAdapter {

    public static final String TAG = "REMINDER_ITEM_ADAPTER";
    private final Context mContext;
    private static Firebase mFirebase;
    private static Firebase mUserRef = null;
    private static Firebase mCompletedRef = null;
    private Firebase mRemindersRef = null;
    private Firebase mLoginInfoRef = null;
    private Firebase mGraphRef = null;
    private String lastCompletedDay = "";
    private AuthData mAuthData;
    private String mUid;
    private long mCurrentStreak;

    private Bitmap check_bp;
    private Bitmap circle_bp;
    private Boolean uncheckAll = false;
    private final List<ReminderItem> mReminderItems = new ArrayList<>();


    public void delete(ReminderItem toRemove){
        mReminderItems.remove(toRemove);
    }
    public int getSize(){
        return mReminderItems.size();
    }

    private int getNumComplete(){
        int i = 0;
        for (ReminderItem item: mReminderItems){
            if (item.getmDayStatus()){
                i++;
            }
        }
        return i;
    }

    private void queueReminder(){
        Firebase notificationsRef = mUserRef.child("notifications");

        notificationsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                String key = "48";
                if (dataSnapshot.getChildren() == null){

                } else {

                    Long min = Long.MAX_VALUE;
                    for (DataSnapshot snapshot: dataSnapshot.getChildren()){
                        if ((Long) snapshot.getValue() > min){
                            min = (Long) snapshot.getValue();
                            key = snapshot.getKey();
                        }
                    }
                }
                Intent myIntent = new Intent(mContext , NotifyService.class);
                AlarmManager alarmManager = (AlarmManager) mContext.getSystemService(mContext.ALARM_SERVICE);
                PendingIntent pendingIntent = PendingIntent.getService(mContext, 0, myIntent, 0);
                Calendar calendar = Calendar.getInstance();
                calendar.set(Calendar.HOUR_OF_DAY, ((Integer.parseInt(key)*15)/60));
                calendar.set(Calendar.MINUTE, Integer.parseInt(key)*15 - 900);
                calendar.set(Calendar.SECOND, 00);
                alarmManager.set(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), pendingIntent);

            }

            @Override
            public void onCancelled(FirebaseError firebaseError) {

            }
        });
    }
    private float uncheckAll(){
        queueReminder();
        Firebase ref = mUserRef.child("reminders");
        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                for (DataSnapshot child : dataSnapshot.getChildren()) {
                    DataSnapshot mDay = child.child("mDayStatus");
                    mDay.getRef().setValue(false);
                    Log.i("Day is false", "DAY FALSE");
                    DataSnapshot mNight = child.child("mNightStatus");
                    mNight.getRef().setValue(false);
                    Log.i("Night false", "Night false");
                }

            }

            @Override
            public void onCancelled(FirebaseError firebaseError) {

            }

        });
        int total = 0, completed = 0;
        for (ReminderItem item: mReminderItems){
            Log.i("Inside unchecked for", "OK");
            if (item.getmDayStatus()){
                total++;
            }
            completed++;
            item.setmDayStatus(false);
            item.setmNightStatus(false);
        }
        notifyDataSetChanged();
        return (float)completed/total;
    }
    private void updateGraph(){
        SimpleDateFormat d = new SimpleDateFormat("MM-dd-yyyy");
        Firebase graphRef = mUserRef.child("graph").child(lastCompletedDay);
        int complete = getNumComplete();
        int total = mReminderItems.size();
        Log.i(ReminderItemAdapter.TAG, "Complete: " + (float)complete+"\n" + "Total: " + (float)total);
        if (total > 0){
            graphRef.setValue((float) complete/total);
        }
    }
    public ReminderItemAdapter(Context context){

        Firebase.setAndroidContext(context);
        mContext = context;
        mFirebase = new Firebase(mContext.getResources().getString(R.string.firebase_url));
        mAuthData = mFirebase.getAuth();
        circle_bp = BitmapFactory.decodeResource(mContext.getResources(), R.drawable.empty_circle);
        check_bp = BitmapFactory.decodeResource(mContext.getResources(), R.drawable.checkmark);

        if(mAuthData != null){
            mUid = mAuthData.getUid();
            mUserRef = new Firebase(mContext.getResources().getString(R.string.firebase_url) + mUid);
            mCompletedRef = new Firebase(mContext.getResources().getString(R.string.firebase_url) + mUid + "/" + "completed/");
            mRemindersRef = new Firebase(mContext.getResources().getString(R.string.firebase_url) + mUid + "/" + "reminders/");
            mLoginInfoRef = new Firebase(mContext.getResources().getString(R.string.firebase_url) + mUid + "/" + "login_info");
            mGraphRef = mUserRef.child("graph");

            //Resets all reminders if it is a new day
            //NOT FINISHED
            mLoginInfoRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    for(DataSnapshot child : dataSnapshot.getChildren()){
                        if(child.getKey().equals("last_completed")){
                            Log.i(LoginActivity.TAG, "Inside on uncheck");
                            String lastCompleted = child.getValue().toString();
                            lastCompletedDay = new String(lastCompleted);
                            SimpleDateFormat date = new SimpleDateFormat("MM-dd-yyyy");
                            Date lastChecked = null;
                            try{
                                lastChecked = date.parse(lastCompleted);
                            }catch(Exception e){
                                Log.i(LoginActivity.TAG, "Date parse failed");
                            }
                            Date curr = new Date();
                            Calendar c1 = Calendar.getInstance();
                            Calendar c2 = Calendar.getInstance();
                            c1.setTime(lastChecked);
                            c2.setTime(curr);

                            /*If the last time a routine was checked is at least a day before the
                            * current time, then reset all of the reminders to be unchecked
                            */
                            if(c2.get(Calendar.DAY_OF_YEAR) > c1.get(Calendar.DAY_OF_YEAR)
                                    && c2.get(Calendar.YEAR) >= c1.get(Calendar.YEAR)){
                                Log.i(LoginActivity.TAG, "Need to reset ");
                                child.getRef().setValue(date.format(curr));
                                //uncheckAll();
                                uncheckAll = true;
                            }
                        }
                    }
                }

                @Override
                public void onCancelled(FirebaseError firebaseError) {
                    Log.i("ON_CANCELLED", "Login_time test failure");
                }
            });
            //Load Reminders into adapter from firebase
            mRemindersRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    if (dataSnapshot.getValue() != null) {
                        Log.i(LoginActivity.TAG, dataSnapshot.getValue().toString());
                        float total = 0, complete = 0;
                        for (DataSnapshot postSnapshot : dataSnapshot.getChildren()) {
                            Log.i(LoginActivity.TAG, postSnapshot.getValue().toString());
                            ReminderItem item = postSnapshot.getValue(ReminderItem.class);
                            item.setReferenceId(postSnapshot.getKey());
                            if(uncheckAll == true){
                                //uncheckAll();
                                if (item.getmDayStatus() || item.getmNightStatus()){
                                    complete++;
                                }
                                total++;
                                item.setmNightStatus(false);
                                item.setmDayStatus(false);
                            }
                            ReminderItemAdapter.this.add(item, false);
                        }
                        if (total > 0) {
                            float ratio = complete / total;
                            mGraphRef.push().setValue(ratio);
                        }
                        if (uncheckAll){
                            uncheckAll();
                            //mGraphRef.push().setValue(ratio);
                            Toast toast = Toast.makeText(mContext, "It's a new day, your routines have been refreshed", Toast.LENGTH_LONG);
                            toast.show();
                        }
                        uncheckAll = false;

                    }

                }

                @Override
                public void onCancelled(FirebaseError firebaseError) {
                    Log.i(LoginActivity.TAG, "The read failed: " + firebaseError.getMessage());
                }
            });
        }
    }
    //Add reminder to adapter, toFirebase boolean should generally be set to true
    public void add(ReminderItem item, Boolean toFirebase){

        /* If toFirebase is set to true, then add the item to Firebase as well */
        if (toFirebase){
            //DONE: - add some kind of firebase functionality?
            Map<String, Object> jsonObj = item.toMap();
            Firebase newRef = mRemindersRef.push();
            newRef.setValue(jsonObj);
            item.addReferenceId(newRef.getKey());
            //DONE: - add some kind of firebase functionality?
            mReminderItems.add(item);
            notifyDataSetChanged();
        } else {
            mReminderItems.add(item);
            notifyDataSetChanged();
        }
    }

    public void clear(){
        mReminderItems.clear();
    }

    @Override
    public Object getItem(int pos){
        return mReminderItems.get(pos);
    }

    @Override
    public long getItemId(int pos){
        return pos;
    }

    @Override
    public int getCount() {
        return mReminderItems.size() ;
    }

    public View getView(int position, View convertView, ViewGroup parent){
        /* Using ViewHolder Pattern for increased performance */

        ViewHolder holder;
        View row = convertView;

        if (row == null){
            row = LayoutInflater.from(mContext).inflate(R.layout.reminder_item, parent, false);
            holder = new ViewHolder();
            holder.title = (TextView) row.findViewById(R.id.reminder_item_text);
            holder.description = (TextView) row.findViewById(R.id.reminder_item_desc);
            holder.completionStatus = (ImageView) row.findViewById(R.id.completion_status_icon);
            row.setTag(holder);
        } else {
            holder = (ViewHolder) row.getTag();
        }

        //Retrieve the current reminderItem
        final ReminderItem reminderItem = (ReminderItem) getItem(position);
        //Set title and description fields
        holder.description.setText(reminderItem.getmDesc());
        holder.title.setText(reminderItem.getmTitle());
        if (beforeNoon()) {
            if (reminderItem.getmDayStatus()) {
                //Set in UI whether item is completed or not
                holder.completionStatus.setImageBitmap(check_bp);
                holder.completionStatus.setClickable(false);
            } else {
                //Set in UI whether item is completed or not
                holder.completionStatus.setImageBitmap(circle_bp);
            }
        } else {
            if (reminderItem.getmNightStatus()) {
                //Set in UI whether item is completed or no
                holder.completionStatus.setImageBitmap(check_bp);
                holder.completionStatus.setClickable(false);
            } else {
                //Set in UI whether item is completed or no
                holder.completionStatus.setImageBitmap(circle_bp);
            }
        }
        row.setOnLongClickListener(new ListView.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                //DONE: open dialog box with option to delete or edit completion status
                //DONE: Implement underlying behavior for dialog box
                AlertDialog.Builder builder = new AlertDialog.Builder(v.getRootView().getContext());
                builder.setMessage("Title: " + reminderItem.getmTitle())
                        .setTitle("Edit Routine")
                        .setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                Log.i(TAG, "Deleting Reminder: " + reminderItem.getmTitle());
                                //Delete reminder from firebase
                                mUserRef.child("reminders").child(reminderItem.getReferenceId()).removeValue();
                                //remove item from adapter
                                mReminderItems.remove(reminderItem);
                                //redraw ListView
                                notifyDataSetChanged();
                                Log.i(TAG, "Deleted Reminder: " + reminderItem.getmTitle());
                            }
                        })
                        .setNegativeButton(R.string.uncheck, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                if (beforeNoon()){
                                    if (reminderItem.getmDayStatus()) {
                                        Log.i(TAG, "Unchecking Reminder(Day): "+reminderItem.getmTitle());
                                        //Change item from completed to incomplete in firebase and in Adapter
                                        mUserRef.child("reminders").child(reminderItem.getReferenceId()).child("mDayStatus").setValue(false);
                                        mUserRef.child("reminders").child(reminderItem.getReferenceId()).child("mNightStatus").setValue(false);
                                        reminderItem.setmDayStatus(false);
                                        reminderItem.setmNightStatus(false);
                                        //redraw list view
                                        notifyDataSetChanged();
                                        removePoint();
                                        Log.i(TAG, "Unchecked Reminder(Day): "+reminderItem.getmTitle());
                                    }
                                } else {
                                    if (reminderItem.getmNightStatus()) {
                                        Log.i(TAG, "Unchecked Reminder(Day): "+reminderItem.getmTitle());
                                        mUserRef.child("reminders").child(reminderItem.getReferenceId()).child("mNightStatus").setValue(false);
                                        mUserRef.child("reminders").child(reminderItem.getReferenceId()).child("mDayStatus").setValue(false);
                                        reminderItem.setmNightStatus(false);
                                        reminderItem.setmDayStatus(false);
                                        //Redraw list view
                                        notifyDataSetChanged();
                                        removePoint();
                                        Log.i(TAG, "Unchecked Reminder(Night): "+reminderItem.getmTitle());
                                    }
                                }
                            }
                        })
                        //Share Button
                        .setNeutralButton("Share", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Log.i(TAG, "Cancel");

                            }
                        });
                AlertDialog alert = builder.create();
                alert.show();
                return true;
            }
        });
        holder.completionStatus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (beforeNoon()) {
                    if (!reminderItem.getmDayStatus()) {
                        ImageView imageView = (ImageView) v;
                        imageView.setImageBitmap(check_bp);
                        ReminderItemAdapter.completeItem(reminderItem);
                        v.setClickable(false);
                    }
                } else {
                    if (!reminderItem.getmNightStatus()) {
                        ImageView imageView = (ImageView) v;
                        imageView.setImageBitmap(check_bp);
                        ReminderItemAdapter.completeItem(reminderItem);
                        v.setClickable(false);
                    }
                }

            }
        });
        return row;

    }

    public static boolean beforeNoon(){
        Calendar c1 = Calendar.getInstance();
        return c1.get(Calendar.HOUR_OF_DAY) < 12;
    }

    public static void completeItem(ReminderItem item){

        Firebase itemRef = mUserRef.child("reminders").child(item.getReferenceId());
        Calendar calendarInstance = Calendar.getInstance();
        calendarInstance.setTime(new Date());
        int currentMinute = calendarInstance.get(Calendar.MINUTE)+calendarInstance.get(Calendar.HOUR_OF_DAY)*60;
        int bucket = currentMinute/15;

        Firebase notificationsRef = mUserRef.child("notifications").child(String.valueOf(bucket));
        notificationsRef.runTransaction(new Transaction.Handler() {
            @Override
            public Transaction.Result doTransaction(MutableData mutableData) {
                if (mutableData.getValue() == null){
                    Log.d("MUTABLE DATA", "MUTABLE DATA IS NULL");
                    mutableData.setValue(1);
                } else {
                    Log.d("MUTABLE DATA", "MUTABLE DATA IS NOT NULL");
                    mutableData.setValue((Long) mutableData.getValue() + 1);
                }
                return Transaction.success(mutableData);
            }

            @Override
            public void onComplete(FirebaseError firebaseError, boolean b, DataSnapshot dataSnapshot) {

            }
        });
        Firebase feedRef = new Firebase("https://routinereminder.firebaseio.com/feed");
        Firebase pushRef = feedRef.push();
        Log.i(ReminderItemAdapter.TAG, "pushRef");
        pushRef.setValue(item.getmTitle());
        Firebase loginRef = mUserRef.child("login_info").child("last_completed");
        Firebase basicStats = mCompletedRef.child(item.getmTitle()).child("basic_stats");
        final Firebase history = mCompletedRef.child(item.getmTitle()).child("history");
        final SimpleDateFormat d2 = new SimpleDateFormat("MM-dd-yyyy");
        String dateString = d2.format(new Date());
        //Firebase graphRef = mUserRef.child("graph").child(dateString).child("total");
        //Firebase graphRef2 = mUserRef.child("graph").child(dateString).child("num_completed");
        //graphRef2.setValue(getNumComplete());
        loginRef.setValue(d2.format(new Date()));
        itemRef.runTransaction(new Transaction.Handler() {
            @Override
            public Transaction.Result doTransaction(MutableData mutableData) {
                if (mutableData.getValue() == null){

                } else {
                    Calendar c = Calendar.getInstance();
                    if (c.get(Calendar.HOUR_OF_DAY) > 12){
                        mutableData.child("mNightStatus").setValue(true);
                        mutableData.child("mDayStatus").setValue(true);
                    } else{
                        mutableData.child("mDayStatus").setValue(true);
                        mutableData.child("mNightStatus").setValue(true);
                    }
                }
                return Transaction.success(mutableData);
            }

            @Override
            public void onComplete(FirebaseError firebaseError, boolean b, DataSnapshot dataSnapshot) {

            }
        });

        basicStats.runTransaction(new Transaction.Handler() {
            @Override
            public Transaction.Result doTransaction(MutableData currentData) {
                Calendar c1 = Calendar.getInstance(); //Yesterday on Calendar
                c1.add(Calendar.DAY_OF_YEAR, -1);
                long current_streak;
                Calendar c2 = Calendar.getInstance(); //Will become the last_completed Date
                Date prev_date;

                SimpleDateFormat d3 = new SimpleDateFormat("MM-dd-yyyy");
                if (currentData.getValue() == null){
                    Log.i(LoginActivity.TAG, "Current Data is null");
                    Map<String, Object> jsonObj = new HashMap<String, Object>();
                    jsonObj.put("last_completed", d3.format(new Date()));
                    jsonObj.put("best_streak", 1);
                    jsonObj.put("current_streak", 1);
                    currentData.setValue(jsonObj);

                    history.child(d2.format(new Date())).setValue(1);
                } else {
                    Log.i(LoginActivity.TAG, "Current Data is NOT null");
                    Object p = currentData.getValue();
                    if (p instanceof HashMap){
                        HashMap dataHash = (HashMap) p;
                        HashMap<String, Object> outputHash = new HashMap<>();
                        try {
                            Log.i("REMINDERLIST ACTIVITY", dataHash.get("last_completed").toString());
                            prev_date = d3.parse(dataHash.get("last_completed").toString());
                            c2.setTime(prev_date);
                            Log.i("REMINDERLIST ACTIVITY", "Year of Yesterday: " + c1.get(Calendar.YEAR) + "\nYear of last_completed: " + c2.get(Calendar.YEAR)
                                    + "\nDay of Yesterday: " + c1.get(Calendar.DAY_OF_YEAR) + "\nDay of last_completed: " + c2.get(Calendar.DAY_OF_YEAR));
                            if (c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR)
                                    && c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)) {
                                Log.i("REMINDERLIST ACTIVITY", "About to Streak");
                                long new_streak = (Long) dataHash.get("current_streak") + 1;
                                long best_streak = (Long) dataHash.get("best_streak");
                                outputHash.put("current_streak", new_streak);
                                if (new_streak > best_streak) {
                                    outputHash.put("best_streak", new_streak);
                                }
                                outputHash.put("last_completed", d3.format(new Date()));
                                current_streak = new_streak;
                                currentData.setValue(outputHash);
                            }else if (c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR)
                                    && c1.get(Calendar.DAY_OF_YEAR)+1 == c2.get(Calendar.DAY_OF_YEAR)){
                                current_streak = (Long) dataHash.get("current_streak");
                                //do nothing
                            } else {
                                outputHash.put("current_streak", 1);
                                outputHash.put("best_streak", dataHash.get("best_streak"));
                                outputHash.put("last_completed", dataHash.get("last_completed"));
                                current_streak = 1;
                                currentData.setValue(outputHash);
                            }
                            history.child(d2.format(new Date())).setValue(current_streak);

                        } catch (ParseException e) {
                            e.printStackTrace();
                        }
                    } else {
                        Log.i(LoginActivity.TAG, "I like balls");
                    }

                }
                return Transaction.success(currentData);
            }

            @Override
            public void onComplete(FirebaseError firebaseError, boolean b, DataSnapshot dataSnapshot) {

            }
        });

        if(beforeNoon()){
            item.setmDayStatus(true);
        } else {
            item.setmNightStatus(true);
        }
        addPoint();
    }
    public static void addPoint(){
        mUserRef.child("points").runTransaction(new Transaction.Handler() {
            @Override
            public Transaction.Result doTransaction(MutableData currentData) {
                if (currentData.getValue() == null){
                    currentData.setValue(0);
                } else {
                    currentData.setValue((Long) currentData.getValue() + 1);
                }
                return Transaction.success(currentData);
            }

            @Override
            public void onComplete(FirebaseError firebaseError, boolean b, DataSnapshot dataSnapshot) {

            }
        });
    }
    public void removePoint(){
        mUserRef.child("points").runTransaction(new Transaction.Handler() {
            @Override
            public Transaction.Result doTransaction(MutableData currentData) {
                if (currentData.getValue() == null) {
                    currentData.setValue(1);
                } else {
                    currentData.setValue((Long) currentData.getValue() - 1);
                }
                return Transaction.success(currentData);
            }

            @Override
            public void onComplete(FirebaseError firebaseError, boolean b, DataSnapshot dataSnapshot) {

            }
        });
    }


    private static class ViewHolder {
        TextView title;
        TextView description;
        ImageView completionStatus;
    }
}