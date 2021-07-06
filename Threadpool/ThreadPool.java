package ir.sharif.math.ap.hw3;

import java.lang.reflect.InvocationTargetException;
import java.util.LinkedList;

public class ThreadPool {

    int threadNumbers;
    final LinkedList<Task> tasks;
    final LinkedList<PThread> threads;

    public ThreadPool(int threadNumbers) {
        this.threadNumbers = threadNumbers;
        tasks = new LinkedList<>();
        threads = new LinkedList<>();
        for (int i = 0; i < threadNumbers; i++){
            threads.add(new PThread());
            threads.get(i).start();
        }
    }

    public int getThreadNumbers() {
            return threadNumbers;
    }

    public void setThreadNumbers(int threadNumbers) {

        synchronized (tasks) {
            if (threadNumbers > this.threadNumbers) {
                int difference = threadNumbers - this.threadNumbers;
                for (int i = 0; i < difference; i++) {
                    threads.add(new PThread());
                    threads.get((i+this.threadNumbers)).start();
                }
            } else {
                int difference = this.threadNumbers - threadNumbers;
                int counter = 0;
                while (counter < difference) {
                    threads.get(0).setDeleted();
                    threads.remove(0);
                    counter++;
                    tasks.notifyAll();
                }
            }
            this.threadNumbers = threadNumbers;
        }
    }

    public void invokeLater(Runnable runnable) {

        synchronized (tasks) {
            tasks.add(new Task(runnable));
            tasks.notifyAll();
        }

    }

    public void invokeAndWait(Runnable runnable) throws InterruptedException, InvocationTargetException {

        Task task = new Task(runnable);
        synchronized (tasks){
            tasks.add(task);
            tasks.notifyAll();
        }
        synchronized (task) {
            while (!task.isComplete) {
                task.wait();
                if (task.getThrowable() != null) {
                    throw new InvocationTargetException(task.getThrowable());
                }
            }
        }

    }

    public void invokeAndWaitUninterruptible(Runnable runnable) throws InvocationTargetException {

        Task task = new Task(runnable);
        synchronized (tasks){
            tasks.add(task);
            tasks.notifyAll();
        }
        synchronized (task) {
            while (!task.isComplete) {
                try {
                    task.wait();
                } catch (InterruptedException exception) {
                    exception.printStackTrace();
                }
                if (task.getThrowable() != null) {
                    throw new InvocationTargetException(task.getThrowable());
                }
            }
        }

    }


    private class PThread extends Thread {

        private boolean isDeleted = false;

        @Override
        public void run() {

            Task task;

            while (!isDeleted) {
                synchronized (tasks) {
                    while (tasks.isEmpty()) {
                        try {
                            tasks.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        if (isDeleted) {
                            return;
                        }
                    }
                    task = tasks.get(0);
                    tasks.remove(0);
                }
                try {
                    task.getRunnable().run();
                    task.isComplete = true;
                    synchronized (task) {
                        task.notifyAll();
                    }
                } catch (Exception throwable) {
                    task.setThrowable(throwable);
                    synchronized (task) {
                        task.notifyAll();
                    }
                }
            }
        }

        public void setDeleted() {
            isDeleted = true;
        }

    }

    private static class Task{

        Exception throwable = null;
        Runnable runnable = null;
        boolean isComplete = false;

        Task(Runnable runnable){
            this.runnable = runnable;
        }

        public void setThrowable(Exception throwable) {
            this.throwable = throwable;
        }

        public Throwable getThrowable() {
            return throwable;
        }

        public Runnable getRunnable() {
            return runnable;
        }

    }

}

