/* Ride request page: pick pickup and drop with place search or by tapping the map. */
(function () {
  var NOMINATIM = "https://nominatim.openstreetmap.org";
  var DEFAULT_CENTER = [28.6139, 77.2090]; // Delhi NCR; change to your city
  var map = L.map("map").setView(DEFAULT_CENTER, 10);
  L.tileLayer("https://tile.openstreetmap.org/{z}/{x}/{y}.png", {
    maxZoom: 19,
    attribution: "&copy; OpenStreetMap contributors"
  }).addTo(map);

  function pin(cls) {
    return L.divIcon({ className: "", html: '<div class="pin ' + cls + '"></div>', iconSize: [18, 18], iconAnchor: [9, 9] });
  }

  var fields = {
    from: { input: byId("from"), lat: byId("fromLat"), lng: byId("fromLng"), list: byId("from-list"), marker: null, icon: pin("rider") },
    to:   { input: byId("to"),   lat: byId("toLat"),   lng: byId("toLng"),   list: byId("to-list"),   marker: null, icon: pin("drop") }
  };
  var active = "from";
  var timers = {};

  function byId(id) { return document.getElementById(id); }

  function setPlace(key, lat, lng, label, noFit) {
    var f = fields[key];
    f.lat.value = lat;
    f.lng.value = lng;
    if (label) { f.input.value = label; }
    if (f.marker) { f.marker.setLatLng([lat, lng]); }
    else { f.marker = L.marker([lat, lng], { icon: f.icon }).addTo(map); }
    f.list.hidden = true;
    if (!noFit) { fitBoth(); }
  }

  function fitBoth() {
    var pts = [];
    ["from", "to"].forEach(function (k) { if (fields[k].marker) { pts.push(fields[k].marker.getLatLng()); } });
    if (pts.length === 2) { map.fitBounds(L.latLngBounds(pts), { padding: [50, 50] }); }
    else if (pts.length === 1) { map.setView(pts[0], 14); }
  }

  function clearPlace(key) {
    var f = fields[key];
    f.lat.value = "";
    f.lng.value = "";
    if (f.marker) { map.removeLayer(f.marker); f.marker = null; }
  }

  // ----- place search (OpenStreetMap Nominatim; please keep requests slow) -----
  function search(key) {
    var f = fields[key];
    var q = f.input.value.trim();
    if (q.length < 3) { f.list.hidden = true; return; }
    fetch(NOMINATIM + "/search?format=jsonv2&limit=5&countrycodes=in&q=" + encodeURIComponent(q))
      .then(function (r) { return r.json(); })
      .then(function (results) {
        f.list.innerHTML = "";
        if (!results.length) { f.list.hidden = true; return; }
        results.forEach(function (place) {
          var li = document.createElement("li");
          li.textContent = place.display_name;
          li.addEventListener("mousedown", function (e) {
            e.preventDefault();
            setPlace(key, parseFloat(place.lat), parseFloat(place.lon), place.display_name);
          });
          f.list.appendChild(li);
        });
        f.list.hidden = false;
      })
      .catch(function () { f.list.hidden = true; });
  }

  ["from", "to"].forEach(function (key) {
    var f = fields[key];
    f.input.addEventListener("input", function () {
      clearPlace(key); // typing again means the old coordinates no longer apply
      clearTimeout(timers[key]);
      timers[key] = setTimeout(function () { search(key); }, 600);
    });
    f.input.addEventListener("focus", function () { active = key; });
    f.input.addEventListener("blur", function () { setTimeout(function () { f.list.hidden = true; }, 150); });
  });

  // ----- tap the map to set the active field -----
  map.on("click", function (e) {
    var key = (!fields.from.lat.value) ? "from" : (!fields.to.lat.value ? "to" : active);
    setPlace(key, e.latlng.lat, e.latlng.lng, "Looking up address...", true);
    fetch(NOMINATIM + "/reverse?format=jsonv2&lat=" + e.latlng.lat + "&lon=" + e.latlng.lng)
      .then(function (r) { return r.json(); })
      .then(function (d) {
        fields[key].input.value = d.display_name || (e.latlng.lat.toFixed(5) + ", " + e.latlng.lng.toFixed(5));
      })
      .catch(function () {
        fields[key].input.value = e.latlng.lat.toFixed(5) + ", " + e.latlng.lng.toFixed(5);
      });
    byId("map-hint").textContent = fields.from.lat.value && fields.to.lat.value
      ? "Tap the map again to move the " + (key === "from" ? "pickup" : "drop") + " point."
      : "Now tap the map to set the drop.";
  });

  // ----- use my location for pickup -----
  byId("use-location").addEventListener("click", function () {
    if (!navigator.geolocation) { showError("Your browser can't share its location. Search for a place instead."); return; }
    navigator.geolocation.getCurrentPosition(function (pos) {
      var lat = pos.coords.latitude, lng = pos.coords.longitude;
      setPlace("from", lat, lng, "Looking up address...");
      fetch(NOMINATIM + "/reverse?format=jsonv2&lat=" + lat + "&lon=" + lng)
        .then(function (r) { return r.json(); })
        .then(function (d) { fields.from.input.value = d.display_name || "My location"; })
        .catch(function () { fields.from.input.value = "My location"; });
    }, function () {
      showError("Couldn't get your location. Allow location access, or search for a place.");
    });
  });

  // ----- validation before submit -----
  function showError(msg) {
    var box = byId("form-error-js");
    box.textContent = msg;
    box.hidden = false;
  }

  byId("ride-form").addEventListener("submit", function (e) {
    var ok = fields.from.lat.value && fields.from.lng.value && fields.to.lat.value && fields.to.lng.value;
    if (!ok) {
      e.preventDefault();
      showError("Pick your pickup and drop places from the suggestion lists, or tap the map.");
    }
  });
})();
