package com.ridepool.web;

import com.ridepool.repo.MessageRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Makes the logged-in user's name and unread message count available to every template (for the top bar). */
@ControllerAdvice
public class GlobalModelAdvice {

    private final MessageRepository messages;

    public GlobalModelAdvice(MessageRepository messages) {
        this.messages = messages;
    }

    @ModelAttribute("userName")
    public String userName(HttpSession session) {
        Object name = session.getAttribute("userName");
        return name == null ? null : name.toString();
    }

    @ModelAttribute("unreadCount")
    public long unreadCount(HttpSession session) {
        Object id = session.getAttribute(AuthInterceptor.SESSION_USER_ID);
        return id == null ? 0L : messages.countByRecipientIdAndSeenFalse((Long) id);
    }
}
