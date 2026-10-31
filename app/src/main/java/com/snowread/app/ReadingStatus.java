package com.snowread.app;

/** Explicit shelf status never changes reading offsets. Missing fields retain legacy progress inference. */
final class ReadingStatus {
    static final int AUTO=0,UNREAD=1,READING=2,FINISHED=3,MAX_MEMO=2000;
    static boolean valid(int status){return status>=AUTO&&status<=FINISHED;}
    static int of(ShelfOrganization.Entry book,float ratio){if(book.readingState!=AUTO)return book.readingState;if(ratio>=1)return FINISHED;return book.readingStarted||ratio>0?READING:UNREAD;}
    static String label(int status){return status==UNREAD?"未读":status==READING?"在读":status==FINISHED?"已读":"跟随阅读进度";}
    private ReadingStatus(){}
}
