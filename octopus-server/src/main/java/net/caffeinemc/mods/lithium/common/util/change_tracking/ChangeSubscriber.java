package net.caffeinemc.mods.lithium.common.util.change_tracking;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.jetbrains.annotations.NotNull;
import java.util.ArrayList;

public interface ChangeSubscriber<T> {

    static <T> ChangeSubscriber<T> combine(ChangeSubscriber<T> prevSubscriber, int prevSData, @NotNull ChangeSubscriber<T> newSubscriber, int newSData) {
        if (prevSubscriber == null) {
            return newSubscriber;
        } else if (prevSubscriber instanceof Multi) {
            ArrayList<ChangeSubscriber<T>> subscribers = new ArrayList<>(((Multi<T>) prevSubscriber).subscribers);
            IntArrayList subscriberDatas = new IntArrayList(((Multi<T>) prevSubscriber).subscriberDatas);
            subscribers.add(newSubscriber);
            subscriberDatas.add(newSData);
            return new Multi<>(subscribers, subscriberDatas);
        } else {
            ArrayList<ChangeSubscriber<T>> subscribers = new ArrayList<>();
            IntArrayList subscriberDatas = new IntArrayList();
            subscribers.add(prevSubscriber);
            subscriberDatas.add(prevSData);
            subscribers.add(newSubscriber);
            subscriberDatas.add(newSData);
            return new Multi<>(subscribers, subscriberDatas);
        }
    }

    static <T> ChangeSubscriber<T> without(ChangeSubscriber<T> prevSubscriber, ChangeSubscriber<T> removedSubscriber) {
        return without(prevSubscriber, removedSubscriber, 0, false);
    }

    static <T> ChangeSubscriber<T> without(ChangeSubscriber<T> prevSubscriber, ChangeSubscriber<T> removedSubscriber, int removedSubscriberData, boolean matchData) {
        if (prevSubscriber == removedSubscriber) {
            return null;
        } else if (prevSubscriber instanceof Multi<T> multi) {
            int index = multi.indexOf(removedSubscriber, removedSubscriberData, matchData);
            if (index != -1) {
                if (multi.subscribers.size() == 2) {
                    return multi.subscribers.get(1 - index);
                } else {
                    ArrayList<ChangeSubscriber<T>> subscribers = new ArrayList<>(multi.subscribers);
                    IntArrayList subscriberDatas = new IntArrayList(multi.subscriberDatas);
                    subscribers.remove(index);
                    subscriberDatas.removeInt(index);

                    return new Multi<>(subscribers, subscriberDatas);
                }
            } else {
                return prevSubscriber;
            }
        } else {
            return prevSubscriber;
        }
    }

    static <T> int dataWithout(ChangeSubscriber<T> prevSubscriber, ChangeSubscriber<T> removedSubscriber, int subscriberData) {
        return dataWithout(prevSubscriber, removedSubscriber, subscriberData, 0, false);
    }

    static <T> int dataWithout(ChangeSubscriber<T> prevSubscriber, ChangeSubscriber<T> removedSubscriber, int subscriberData, int removedSubscriberData, boolean matchData) {
        if (prevSubscriber instanceof Multi<T> multi) {
            int index = multi.indexOf(removedSubscriber, removedSubscriberData, matchData);
            if (index != -1) {
                if (multi.subscribers.size() == 2) {
                    return multi.subscriberDatas.getInt(1 - index);
                } else {
                    return subscriberData;
                }
            } else {
                return subscriberData;
            }
        }
        return prevSubscriber == removedSubscriber ? 0 : subscriberData;
    }

    static int dataOf(ChangeSubscriber<?> subscribers, ChangeSubscriber<?> subscriber, int subscriberData) {
        return subscribers instanceof Multi<?> multi ? multi.subscriberDatas.getInt(multi.subscribers.indexOf(subscriber)) : subscriberData;
    }

    static <T> boolean containsSubscriber(ChangeSubscriber<T> subscriber, int subscriberData, ChangeSubscriber<T> subscriber1, int subscriberData1) {
        if (subscriber instanceof Multi<T> multi) {
            return multi.indexOf(subscriber1, subscriberData1, true) != -1;
        }
        return subscriber == subscriber1 && subscriberData == subscriberData1;
    }


    /**
     * Notify the subscriber that the publisher's count will change immediately after this call.
     */
    void lithium$notifyCount(T publisher, int subscriberData, int newCount);

    class Multi<T> implements ChangeSubscriber<T> {
        private final ArrayList<ChangeSubscriber<T>> subscribers;
        private final IntArrayList subscriberDatas;

        public Multi(ArrayList<ChangeSubscriber<T>> subscribers, IntArrayList subscriberDatas) {
            this.subscribers = subscribers;
            this.subscriberDatas = subscriberDatas;
        }

        @Override
        public void lithium$notifyCount(T publisher, int subscriberData, int newCount) {
            ArrayList<ChangeSubscriber<T>> changeSubscribers = this.subscribers;
            for (int i = 0; i < changeSubscribers.size(); i++) {
                ChangeSubscriber<T> subscriber = changeSubscribers.get(i);
                subscriber.lithium$notifyCount(publisher, this.subscriberDatas.getInt(i), newCount);
            }
        }

        int indexOf(ChangeSubscriber<T> subscriber, int subscriberData, boolean matchData) {
            if (!matchData) {
                return this.subscribers.indexOf(subscriber);
            } else {
                for (int i = 0; i < this.subscribers.size(); i++) {
                    if (this.subscribers.get(i) == subscriber && this.subscriberDatas.getInt(i) == subscriberData) {
                        return i;
                    }
                }
                return -1;
            }
        }
    }
}
