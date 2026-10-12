package com.ridepool.service;

import com.ridepool.model.Message;
import com.ridepool.model.User;
import com.ridepool.repo.MessageRepository;
import com.ridepool.repo.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * In-app chat. You can message someone when you currently match each other's ride,
 * or when you already have a conversation with them.
 */
@Service
public class MessageService {

    private static final int MAX_LENGTH = 1000;
    private static final String DEMO_EMAIL_SUFFIX = "@ridepool.demo";
    private static final String[] DEMO_REPLIES = {
            "Hi! Yes, I'm going the same way. Happy to share.",
            "Sounds good. I'll be at the pickup point 5 minutes early.",
            "Great, see you there. Message me if anything changes.",
            "Works for me. Should we split the fare equally?"
    };

    private final MessageRepository messages;
    private final UserRepository users;
    private final MatchService matcher;
    private final Random random = new Random();

    public MessageService(MessageRepository messages, UserRepository users, MatchService matcher) {
        this.messages = messages;
        this.users = users;
        this.matcher = matcher;
    }

    @Transactional(readOnly = true)
    public boolean canChat(Long me, Long other) {
        if (me == null || other == null || me.equals(other) || !users.existsById(other)) {
            return false;
        }
        return messages.countBetween(me, other) > 0 || matcher.areMatched(me, other);
    }

    /** Sends a message. Returns null if it is empty or the two users are not allowed to chat. */
    @Transactional
    public Message send(Long me, Long other, String body) {
        String text = body == null ? "" : body.trim();
        if (text.isEmpty() || !canChat(me, other)) {
            return null;
        }
        if (text.length() > MAX_LENGTH) {
            text = text.substring(0, MAX_LENGTH);
        }
        User sender = users.findById(me).orElseThrow();
        User recipient = users.findById(other).orElseThrow();

        Message m = new Message();
        m.setSender(sender);
        m.setRecipient(recipient);
        m.setBody(text);
        m = messages.save(m);

        // Demo riders are not real people, so they answer automatically.
        if (recipient.getEmail().endsWith(DEMO_EMAIL_SUFFIX)) {
            Message reply = new Message();
            reply.setSender(recipient);
            reply.setRecipient(sender);
            reply.setBody(DEMO_REPLIES[random.nextInt(DEMO_REPLIES.length)]);
            messages.save(reply);
        }
        return m;
    }

    /** Messages in the conversation after afterId; also marks the other person's messages as read. */
    @Transactional
    public List<Message> thread(Long me, Long other, long afterId) {
        List<Message> list = messages.thread(me, other, afterId);
        messages.markSeen(other, me);
        return list;
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long me) {
        return messages.countByRecipientIdAndSeenFalse(me);
    }

    @Transactional(readOnly = true)
    public List<Conversation> inbox(Long me) {
        Map<Long, Conversation> byPerson = new LinkedHashMap<>();
        for (Message m : messages.involving(me)) { // newest first, so the first hit per person is the latest
            User other = m.getSender().getId().equals(me) ? m.getRecipient() : m.getSender();
            if (!byPerson.containsKey(other.getId())) {
                long unread = messages.countBySenderIdAndRecipientIdAndSeenFalse(other.getId(), me);
                byPerson.put(other.getId(), new Conversation(other, m, unread));
            }
        }
        return new ArrayList<>(byPerson.values());
    }
}
