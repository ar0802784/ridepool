package com.ridepool.web;

import com.ridepool.model.Message;
import com.ridepool.model.User;
import com.ridepool.repo.UserRepository;
import com.ridepool.service.MessageService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class ChatController {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("hh:mm a");

    private final MessageService chat;
    private final UserRepository users;

    public ChatController(MessageService chat, UserRepository users) {
        this.chat = chat;
        this.users = users;
    }

    /** Inbox: everyone you have talked to. */
    @GetMapping("/messages")
    public String inbox(HttpSession session, Model model) {
        model.addAttribute("conversations", chat.inbox(me(session)));
        model.addAttribute("meId", me(session));
        return "messages";
    }

    /** One conversation. The optional draft pre-fills the message box. */
    @GetMapping("/chat/{userId}")
    public String chatPage(@PathVariable Long userId,
                           @RequestParam(required = false) String draft,
                           HttpSession session, Model model) {
        Long me = me(session);
        User other = users.findById(userId).orElse(null);
        if (other == null || !chat.canChat(me, userId)) {
            return "redirect:/messages";
        }
        List<Message> thread = chat.thread(me, userId, 0L);
        long lastId = thread.isEmpty() ? 0L : thread.get(thread.size() - 1).getId();

        model.addAttribute("other", other);
        model.addAttribute("messages", thread);
        model.addAttribute("meId", me);
        model.addAttribute("lastId", lastId);
        model.addAttribute("draft", draft == null ? "" : draft);
        return "chat";
    }

    /** Plain form fallback, used if JavaScript is off. */
    @PostMapping("/chat/{userId}")
    public String sendForm(@PathVariable Long userId, @RequestParam String body, HttpSession session) {
        chat.send(me(session), userId, body);
        return "redirect:/chat/" + userId;
    }

    /** Used by the chat page script to send without reloading. */
    @PostMapping("/api/chat/{userId}")
    @ResponseBody
    public Map<String, Object> sendJson(@PathVariable Long userId, @RequestParam String body, HttpSession session) {
        Message sent = chat.send(me(session), userId, body);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", sent != null);
        return out;
    }

    /** Polled by the chat page for new messages. */
    @GetMapping("/api/chat/{userId}")
    @ResponseBody
    public Map<String, Object> pollJson(@PathVariable Long userId,
                                        @RequestParam(defaultValue = "0") long after,
                                        HttpSession session) {
        Long me = me(session);
        List<Map<String, Object>> list = new ArrayList<>();
        if (chat.canChat(me, userId)) {
            for (Message m : chat.thread(me, userId, after)) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", m.getId());
                row.put("mine", m.getSender().getId().equals(me));
                row.put("body", m.getBody());
                row.put("time", m.getSentAt().format(TIME));
                list.add(row);
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("messages", list);
        return out;
    }

    private Long me(HttpSession session) {
        return (Long) session.getAttribute(AuthInterceptor.SESSION_USER_ID);
    }
}
