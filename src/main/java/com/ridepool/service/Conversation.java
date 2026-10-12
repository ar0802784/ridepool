package com.ridepool.service;

import com.ridepool.model.Message;
import com.ridepool.model.User;

/** One line in the inbox: the other person, the latest message, and how many are unread. */
public class Conversation {

    private final User other;
    private final Message last;
    private final long unread;

    public Conversation(User other, Message last, long unread) {
        this.other = other;
        this.last = last;
        this.unread = unread;
    }

    public User getOther() { return other; }
    public Message getLast() { return last; }
    public long getUnread() { return unread; }
}
