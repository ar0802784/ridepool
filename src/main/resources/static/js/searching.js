/* Searching page: draws the route, polls for co-riders, and moves on to the results when someone is found. */
(function () {
  var app = document.getElementById("app");
  var id = app.dataset.id;
  var from = [parseFloat(app.dataset.fromLat), parseFloat(app.dataset.fromLng)];
  var to = [parseFloat(app.dataset.toLat), parseFloat(app.dataset.toLng)];
  var POLL_MS = 4000;
  var redirecting = false;

  var map = L.map("map");
  L.tileLayer("https://tile.openstreetmap.org/{z}/{x}/{y}.png", {
    maxZoom: 19,
    attribution: "&copy; OpenStreetMap contributors"
  }).addTo(map);

  var youIcon = L.divIcon({
    className: "",
    html: '<div class="pulse-marker"><div class="ring"></div><div class="dot"></div></div>',
    iconSize: [18, 18], iconAnchor: [9, 9]
  });
  var dropIcon = L.divIcon({ className: "", html: '<div class="pin drop"></div>', iconSize: [18, 18], iconAnchor: [9, 9] });
  var riderIcon = L.divIcon({ className: "", html: '<div class="pin rider"></div>', iconSize: [18, 18], iconAnchor: [9, 9] });

  L.marker(from, { icon: youIcon }).addTo(map).bindTooltip("You are here");
  L.marker(to, { icon: dropIcon }).addTo(map).bindTooltip("Drop");
  map.fitBounds(L.latLngBounds([from, to]), { padding: [50, 50] });

  // Road route from the free OSRM demo server; falls back to a straight dashed line.
  var straight = L.polyline([from, to], { color: "#0e4f3f", weight: 3, dashArray: "6 8" }).addTo(map);
  fetch("https://router.project-osrm.org/route/v1/driving/" +
        from[1] + "," + from[0] + ";" + to[1] + "," + to[0] + "?overview=full&geometries=geojson")
    .then(function (r) { return r.json(); })
    .then(function (d) {
      if (d.routes && d.routes.length) {
        var coords = d.routes[0].geometry.coordinates.map(function (c) { return [c[1], c[0]]; });
        map.removeLayer(straight);
        var line = L.polyline(coords, { color: "#0e4f3f", weight: 4 }).addTo(map);
        map.fitBounds(line.getBounds(), { padding: [50, 50] });
      }
    })
    .catch(function () { /* keep the straight line */ });

  // ----- riders -----
  var riderLayer = L.layerGroup().addTo(map);

  function showRiders(riders) {
    riderLayer.clearLayers();
    riders.forEach(function (r) {
      var el = document.createElement("div");
      var title = document.createElement("strong");
      title.textContent = r.name;
      el.appendChild(title);
      var info = document.createElement("div");
      info.textContent = r.pickupKm + " km from your pickup" + (r.rating ? ", rated " + r.rating + " out of 5" : ", no ratings yet");
      el.appendChild(info);
      L.marker([r.lat, r.lng], { icon: riderIcon }).addTo(riderLayer).bindPopup(el);
    });
  }

  function poll() {
    if (redirecting) { return; }
    fetch("/api/matches/" + id, { headers: { "Accept": "application/json" } })
      .then(function (r) {
        if (!r.ok) { throw new Error("poll failed"); }
        return r.json();
      })
      .then(function (data) {
        showRiders(data.riders);
        if (data.count > 0 && !redirecting) {
          redirecting = true;
          document.getElementById("spinner").hidden = true;
          document.getElementById("status-title").textContent =
            data.count === 1 ? "1 rider found" : data.count + " riders found";
          document.getElementById("status-text").textContent = "Taking you to their details and the fare comparison.";
          setTimeout(function () { window.location.href = "/results/" + id; }, 2500);
        }
      })
      .catch(function () { /* try again on the next tick */ });
  }

  poll();
  setInterval(poll, POLL_MS);

  // ----- demo riders -----
  var demoBtn = document.getElementById("demo-btn");
  demoBtn.addEventListener("click", function () {
    demoBtn.disabled = true;
    demoBtn.textContent = "Adding demo riders...";
    fetch("/api/demo/" + id, { method: "POST" })
      .then(function () { poll(); })
      .finally(function () { demoBtn.textContent = "Demo riders added"; });
  });
})();
