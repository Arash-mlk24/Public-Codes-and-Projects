package ir.sharif.math.ap.hw3;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class JobRunner {

    HashMap<String, Integer> changeableResources = new HashMap<>();
    volatile int threadNumbers;
    volatile int runningThreads = 0;
    final PLock pLock = new PLock();
    final ThreadPool threadPool;
    final Boss boss;
    private final Object mainObj = new Object();


    public JobRunner(Map<String, Integer> resources, List<Job> jobs, int initialThreadNumber) {

        threadPool = new ThreadPool(initialThreadNumber);
        threadNumbers = initialThreadNumber;
        boss = new Boss(jobs, resources);
        boss.start();

    }

    public void setThreadNumbers(int threadNumbers) {
        pLock.lock1();

        threadPool.setThreadNumbers(threadNumbers);
        if (threadNumbers > this.threadNumbers) {
            synchronized (mainObj) {
                mainObj.notify();
            }
        }
        this.threadNumbers = threadNumbers;

        pLock.release();
    }

    private class Boss extends Thread {

        ArrayList<CapableRunnable> newJobs = new ArrayList<>();

        public Boss(List<Job> jobs, Map<String, Integer> resources) {
            for (Job job : jobs) {
                newJobs.add(new CapableRunnable(job));
            }
            for (Map.Entry<String,Integer> entry : resources.entrySet()){
                changeableResources.put(entry.getKey(),entry.getValue());
            }
        }

        @Override
        public void run() {
            ArrayList<CapableRunnable> willRemove = new ArrayList<>();
            while (!newJobs.isEmpty()) {

                pLock.lock3();
                for (CapableRunnable job : newJobs) {
                    if (capableToWork(job)) {
                        if (isEnoughResource(job)) {
                            doJob(job);
                            runningThreads++;
                            willRemove.add(job);
                        }
                    }
                }
                newJobs.removeAll(willRemove);
                pLock.release();

                synchronized (mainObj) {
                    try {
                        mainObj.wait();
                    } catch (InterruptedException interruptedException) {
                        interruptedException.printStackTrace();
                    }
                }

            }
        }

        private boolean capableToWork(CapableRunnable capableRunnable) {
            for (String resource : capableRunnable.job.getResources()) {
                if (changeableResources.get(resource) <= 0) {
                    return false;
                }
            }
            return isThreadAvailable();
        }

        private boolean isThreadAvailable() {
            return ( (threadNumbers - runningThreads) > 0 );
        }

        private void doJob(CapableRunnable capableRunnable) {
            for (String resource : capableRunnable.job.getResources()) {
                changeableResources.put(resource, changeableResources.get(resource) - 1);
            }
            threadPool.invokeLater(capableRunnable);
        }

        private boolean isEnoughResource(CapableRunnable capableRunnable) {
            LinkedList<String> demand = new LinkedList<>(capableRunnable.job.getResources());
            for (String resource : demand) {
                if (changeableResources.containsKey(resource)) {
                    if (!(changeableResources.get(resource) > 0)) {
                        return false;
                    }
                } else {
                    return false;
                }
            }
            return true;
        }

    }

    private class CapableRunnable implements Runnable {

        Job job;
        long runResult;

        CapableRunnable(Job job) {
            this.job = job;
        }

        @Override
        public void run() {

            runResult = job.getRunnable().run();
            pLock.lock2();
            try {
                Thread.sleep(runResult);
            } catch (InterruptedException ignored) {}
            freeUpResources(job);
            runningThreads--;
            synchronized (mainObj) {
                mainObj.notifyAll();
            }
            pLock.release();

        }

        private void freeUpResources(Job job) {
            for (String returnValue : job.getResources()) {
                changeableResources.put(returnValue, (changeableResources.get(returnValue) + 1) );
            }
        }

    }

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

        private class Task{

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

    private class PLock {

        private final Object lockSyncObj = new Object();
        private LinkedList<Integer> waitObjects = new LinkedList<>();
        boolean locked;

        public PLock() {
            this.locked = false;
            this.waitObjects.add(0);
            this.waitObjects.add(0);
            this.waitObjects.add(0);
        }

        public void lock1() {

            synchronized (lockSyncObj) {
                waitObjects.set(0, waitObjects.get(0)+1);
                while (locked) {
                    try {

                        lockSyncObj.wait();


                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }


                }
                waitObjects.set(0, waitObjects.get(0)-1);
                locked = true;
            }

        }

        void lock2() {

            synchronized (lockSyncObj) {
                waitObjects.set(1, waitObjects.get(1)+1);
                while (locked || waitObjects.get(0) != 0) {
                    try {

                        lockSyncObj.wait();


                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }


                }
                waitObjects.set(1, waitObjects.get(1)-1);
                locked = true;
            }

        }

        void lock3() {

            synchronized (lockSyncObj) {
                waitObjects.set(2, waitObjects.get(2)+1);
                while (locked || waitObjects.get(0) != 0 || waitObjects.get(1) != 0) {
                    try {

                        lockSyncObj.wait();


                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }


                }
                waitObjects.set(2, waitObjects.get(2)-1);
                locked = true;
            }

        }

        void release() {
            synchronized (lockSyncObj) {
                locked = false;
                lockSyncObj.notifyAll();
            }
        }

    }
}

