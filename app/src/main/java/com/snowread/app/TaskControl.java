package com.snowread.app;

/** Cooperative cancellation. The final directory swap is one non-cancellable commit. */
final class TaskControl implements BackupArchive.Progress {
    static final class Cancelled extends RuntimeException {Cancelled(){super("操作已取消");}}
    private final BackupArchive.Progress listener;
    private boolean cancelled,committing;
    TaskControl(BackupArchive.Progress listener){this.listener=listener;}
    synchronized boolean cancel(){if(committing)return false;cancelled=true;return true;}
    synchronized boolean committing(){return committing;}
    public synchronized void checkCancelled(){if(cancelled||Thread.currentThread().isInterrupted())throw new Cancelled();}
    public synchronized void beginCommit(){checkCancelled();committing=true;}
    public void update(int done,int total,String title){checkCancelled();listener.update(done,total,title);}
}
