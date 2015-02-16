package com.konukoii.smokesignals;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.telephony.gsm.SmsMessage;
import android.util.Log;
import android.widget.Toast;
import android.telephony.SmsManager;
import java.util.ArrayList;
import java.util.Date;
import android.content.IntentFilter;
import android.provider.CallLog;
import android.database.Cursor;
import android.content.ContentResolver;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.PhoneLookup;
import android.content.ContentResolver;

/**
 * Created by TransAtlantic on 2/14/2015.
 */



public class SMSRequestManager {

    //Debuggin' Purpouses
    private final static String TAG="SmokeSignals";

    //Void Read from file the messageCue

    private final static int LOCATION = 1;
    private final static int CONTACTSEARCH = 2;
    private final static int MISSEDCALLS = 3;
    private final static int BATTERYLIFE = 4;
    private final static int RING = 5;
    private final static int HELP = 6;

    private final static String HELP_TXT = "TEXT ME:\n'//Location' <- To query GPS coordinates\n" +
                                                    "'//Contact [name]' <- For contact search\n" +
                                                    "'//Calls' <- To query missed calls\n" +
                                                    "'//Battery' <-To query battery life\n"+
                                                    "'//Ring' <-For phone to start ringing (for 2 Minutes)\n"+
                                                    "'//Help' <-To display this help menu again\n";


    Context context;    //The context that called this
    Intent intent;      //The intent that called this
    String msg_from;    //Who is the app talking to

    //Void Go
        //Main thing from where everything stems from
    public void go(Context context, Intent intent){
        this.context = context;
        this.intent = intent;

        //Toast.makeText(context, "Pana te llego un mensaje!", Toast.LENGTH_LONG).show();
        Log.d(TAG, "New SMS Arrived");
        Bundle bundle = intent.getExtras();
        SmsMessage[] msgs = null;
        msg_from="";
        String msg_body="";
        if (bundle != null) {
            try {
                Object[] pdus = (Object[]) bundle.get("pdus");
                msgs = new SmsMessage[pdus.length];
                for (int i = 0; i < msgs.length; i++) {
                    msgs[i] = SmsMessage.createFromPdu((byte[]) pdus[i]);
                    msg_from = msgs[i].getOriginatingAddress();
                    msg_body = msgs[i].getMessageBody();
                }

                //Toast.makeText(context, msg_from, Toast.LENGTH_LONG).show();
                //Toast.makeText(context, msg_body, Toast.LENGTH_LONG).show();

                int i = parseSMS(msg_body);
                if (i==HELP){
                    Toast.makeText(context, "Help?", Toast.LENGTH_LONG).show();
                    QueryHelp();
                }
                else if (i==BATTERYLIFE){
                    Toast.makeText(context, "Battery?", Toast.LENGTH_LONG).show();
                    QueryBattery();
                }
                else if (i==MISSEDCALLS){
                    Toast.makeText(context, "Calls?", Toast.LENGTH_LONG).show();
                    QueryMissedCalls();
                }

                //Toast.makeText(context, "text"+i, Toast.LENGTH_LONG).show();
                //Toast.makeText(context, "sh",Toast.LENGTH_LONG).show();

            } catch (Exception e) {
                Log.d(TAG, e.getMessage());
            }
        }
    }

    //ParseCmd
    private int parseSMS(String msg_body){
        if (msg_body.equals("//Location")){
            return LOCATION;
        }
        else if (msg_body.equals("//Ring")){
            return RING;
        }
        else if (msg_body.equals("//Battery")){
            return BATTERYLIFE;
        }
        else if (msg_body.equals("//Calls")){
            return MISSEDCALLS;
        }
        else if (msg_body.equals("//Help")){
            return HELP;
        }
        else if (msg_body.substring(0,9).equals("//Contact")){ //else if (msg_body.substring(0,9).equals("//Contact")){
            QueryContact(msg_body.substring(10));
            //sendSMS(msg_from,msg_body.substring(10));
            return CONTACTSEARCH;
        }


        return 0;
    }


    //Respond_to_SMS
    private void sendSMS(String phoneNumber, String message){

        SmsManager sms = SmsManager.getDefault();
        ArrayList<String> parts = sms.divideMessage(message);
        sms.sendMultipartTextMessage(phoneNumber, null, parts, null, null);
        //sms.sendTextMessage(phoneNumber, null, message, null, null);
        Log.d(TAG,"message sent!");
    }

    //Query Functions//////////////////////////////////////////////////////////////////////////////
    private void QueryHelp(){
        sendSMS(msg_from, HELP_TXT);
    }

    private void QueryBattery(){
        context.registerReceiver(this.mBatInfoReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    }

    private void QueryMissedCalls(){

        //Get all the Call Log
        String[] projection = { CallLog.Calls.CACHED_NAME, CallLog.Calls.CACHED_NUMBER_LABEL, CallLog.Calls.TYPE };

        //Query to find which calls in the Call Log are MISSED and NEW (Haven't been awknoledged by user)
        String where = CallLog.Calls.TYPE+"="+CallLog.Calls.MISSED_TYPE+" AND NEW = 1";
        Cursor c = context.getContentResolver().query(CallLog.Calls.CONTENT_URI,null,where, null, null);

        //Check if there's no missed calls...or negative missed calls? :S
        if (c.getCount() <=0){
            sendSMS(msg_from,"No missed calls...no she didn't call back...yes its because she found you weird..."); //lulz remember to change this
        }

        //Make a nice list of missed calls (Hopefully you don't have 42 missed phone calls from you girlfriend in the last hour)
        c.moveToFirst();



        String output="MISSED CALLS:";
        int number = c.getColumnIndex(CallLog.Calls.NUMBER);
        int name = c.getColumnIndex(CallLog.Calls.CACHED_NAME);
        int date = c.getColumnIndex(CallLog.Calls.DATE);

        do{ //Because you know you have at least one

            String phNumber = c.getString(number);
            String callDate = c.getString(date);
            String callerName = c.getString(name);
            Date callDayTime = new Date(Long.valueOf(callDate));

            output+="\nName: "+callerName+"\nPhone Number: " + phNumber +"\nCall Date: " + callDayTime;
            output+="\n-------";

        }while(c.moveToNext());

        sendSMS(msg_from,output);

        Log.d(TAG, output); //do some other operation

    }

    //COPIED THIS FROM THE INTERWEBS. NO IDEA HOW THIS IS WORKING! SO CONFUSED ABOUT THIS
    //SAUCE: http://stackoverflow.com/questions/9625308/android-find-a-contact-by-display-name
    //TO DO: (In order of easiness)
    // --> DONT SEND EMAIL IF EMAIL IS NULL
    // --> SEND MESSAGE BACK IF CONTACT NOT FOUND
    // --> FIND MULTIPLE CONTACTS
    // --> FIND CONTACTS WITH ONLY PARTIAL INFO
    private void QueryContact(String query){

         /*
        query = "%"+query+"%"; //Super important for the SQL LIKE command (LIKE %ed% returns true to EDuardo, pEDro, etc. (also LIKE is not case sensitive!)
        String id_name=null;
        Uri resultUri = ContactsContract.Contacts.CONTENT_URI;
        Cursor cont = context.getContentResolver().query(resultUri, null, null, null, null);
        String whereName = ContactsContract.Data.MIMETYPE + " = ? AND " + ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME + " LIKE ?" ;
        String[] whereNameParams = new String[] { ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,query};
        Cursor nameCur = context.getContentResolver().query(ContactsContract.Data.CONTENT_URI, null, whereName, whereNameParams, ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME);

        while (nameCur.moveToNext()) {
            id_name = nameCur.getString(nameCur.getColumnIndex(ContactsContract.CommonDataKinds.StructuredName.CONTACT_ID));}
        nameCur.close();
        cont.close();
        nameCur.close();
        */

        //Find the ID
        query = "%"+query+"%"; //Super important for the SQL LIKE command (LIKE %ed% returns true to EDuardo, pEDro, etc. (also LIKE is not case sensitive!)
        String id_name=null;
        Uri resultUri = ContactsContract.Contacts.CONTENT_URI;
        Cursor cont = context.getContentResolver().query(resultUri, null, null, null, null);
        String whereName = ContactsContract.Data.MIMETYPE + " = ? AND " + ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME + " LIKE ?" ;
        String[] whereNameParams = new String[] { ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,query};
        Cursor nameCur = context.getContentResolver().query(ContactsContract.Data.CONTENT_URI, null, whereName, whereNameParams, ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME);

        while (nameCur.moveToNext()) {
            id_name = nameCur.getString(nameCur.getColumnIndex(ContactsContract.CommonDataKinds.StructuredName.CONTACT_ID));}
        nameCur.close();
        cont.close();
        nameCur.close();

        String id = id_name;


        //Find the REST;
        String name=null;
        String phone=null;
        String email=null;
        resultUri = ContactsContract.Contacts.CONTENT_URI;
        cont = context.getContentResolver().query(resultUri, null, null, null, null);
        whereName = ContactsContract.Data.MIMETYPE + " = ? AND " + ContactsContract.CommonDataKinds.StructuredName.CONTACT_ID+ " = ?" ;

        String[] whereNameParams1 = new String[] { ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,id};
        Cursor nameCur1 = context.getContentResolver().query(ContactsContract.Data.CONTENT_URI, null, whereName, whereNameParams1, ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME);
        while (nameCur1.moveToNext()) {
            name = nameCur1.getString(nameCur1.getColumnIndex(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME));}
        nameCur1.close();
        cont.close();
        nameCur1.close();


        String[] whereNameParams2 = new String[] { ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,id};
        Cursor nameCur2 = context.getContentResolver().query(ContactsContract.Data.CONTENT_URI, null, whereName, whereNameParams2, ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME);
        while (nameCur2.moveToNext()) {
            phone = nameCur2.getString(nameCur2.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));}
        nameCur2.close();
        cont.close();
        nameCur2.close();


        String[] whereNameParams3 = new String[] { ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,id};
        Cursor nameCur3 = context.getContentResolver().query(ContactsContract.Data.CONTENT_URI, null, whereName, whereNameParams3, ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME);
        while (nameCur3.moveToNext()) {
            email = nameCur3.getString(nameCur3.getColumnIndex(ContactsContract.CommonDataKinds.Email.DATA));}
        nameCur3.close();
        cont.close();
        nameCur3.close();

        String[] whereNameParams4 = new String[] { ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE,id};
        Cursor nameCur4 = context.getContentResolver().query(ContactsContract.Data.CONTENT_URI, null, whereName, whereNameParams4, ContactsContract.CommonDataKinds.StructuredPostal.DATA);
        while (nameCur4.moveToNext()) {
            phone = nameCur4.getString(nameCur4.getColumnIndex(ContactsContract.CommonDataKinds.StructuredPostal.DATA));}
        nameCur4.close();
        cont.close();
        nameCur4.close();
        //showing result
        sendSMS(msg_from, "Name= " + name + "\nPhone= " + phone + "\nEmail= " + email);

    }

    //Broadcast Receivers Inner Classes////////////////////////////////////////////////////////////
    ///Gotta do it this way since BroadCast Receivers take a while to anwser back these queries///
    private BroadcastReceiver mBatInfoReceiver = new BroadcastReceiver(){
        @Override
        public void onReceive(Context arg0, Intent intent) {
            int level = intent.getIntExtra("level", 0);
            sendSMS(msg_from,"Battery Level: "+level+"%");
            Log.d(TAG,"Sent Battery Level");
            context.unregisterReceiver(this);
        }
    };



    //Blacklist or Whitelist Phones

    //Parse String
        //Is it really a request for us or just a random message


    //Find Location

}