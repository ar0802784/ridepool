/* Chat page: sends messages without reloading and checks for new ones every 3 seconds. */
(function () {
  var log = document.getElementById("chat-log");
  var form = document.getElementById("chat-form");
  var box = document.getElementById("body");
  var userId = log.dataset.user;
  var lastId = parseInt(log.dataset.last || "0", 10);
  var busy = false;

  function scrollDown() { log.scrollTop = log.scrollHeight; }

  function addBubble(m) {
    var empty = document.getElementById("chat-empty");
    if (empty) { empty.remove(); }
    var div = document.createElement("div");
    div.className = "bubble " + (m.mine ? "mine" : "theirs");
    var body = document.createElement("span");
    body.className = "body";
    body.textContent = m.body;          // textContent keeps user text from being treated as HTML
    var time = document.createElement("span");
    time.className = "time";
    time.textContent = m.time;
    div.appendChild(body);
    div.appendChild(time);
    log.appendChild(div);
  }

  function poll() {
    if (busy) { return Promise.resolve(); }
    busy = true;
    return fetch("/api/chat/" + userId + "?after=" + lastId, { headers: { "Accept": "application/json" } })
      .then(function (r) { if (!r.ok) { throw new Error("poll failed"); } return r.json(); })
      .then(function (data) {
        data.messages.forEach(function (m) {
          if (m.id > lastId) { addBubble(m); lastId = m.id; }
        });
        if (data.messages.length) { scrollDown(); }
      })
      .catch(function () { /* try again next time */ })
      .then(function () { busy = false; });
  }

  form.addEventListener("submit", function (e) {
    e.preventDefault();
    var text = box.value.trim();
    if (!text) { return; }
    var send = form.querySelector("button");
    send.disabled = true;
    fetch("/api/chat/" + userId, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: "body=" + encodeURIComponent(text)
    })
      .then(function (r) { return r.json(); })
      .then(function (d) {
        if (d.ok) { box.value = ""; return poll(); }
        alert("Couldn't send that message. Check that you still match this rider.");
      })
      .catch(function () { alert("Couldn't send that message. Check your connection and try again."); })
      .then(function () { send.disabled = false; box.focus(); });
  });

  // Enter sends, Shift+Enter adds a new line
  box.addEventListener("keydown", function (e) {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      form.requestSubmit();
    }
  });

  scrollDown();
  setInterval(poll, 3000);
})();
