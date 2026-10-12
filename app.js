/**
 * RidePool - Client-side Interactive Engine for GitHub Pages & Live Demo
 * Replicates full backend matching, fare calculation, chat simulation, and trip storage.
 */
(function () {
  "use strict";

  // --- Configuration & Constants ---
  const NOMINATIM = "https://nominatim.openstreetmap.org";
  const DEFAULT_CENTER = [28.6139, 77.2090]; // Delhi NCR
  const DEMO_NAMES = ["Aarav", "Diya", "Kabir", "Meera", "Rohan", "Ishita"];
  const DEMO_STARS = [5, 4, 5, 4, 3, 5];
  const DEMO_REPLIES = [
    "Hi! Yes, I'm going the same way. Happy to share.",
    "Sounds good. I'll be at the pickup point 5 minutes early.",
    "Great, see you there! Message me if anything changes.",
    "Works for me. Splitting the fare will save us both money!"
  ];

  const RATES = [
    { provider: "Rapido", vehicle: "Bike taxi", base: 20, perKm: 7.5, perMin: 0.4, minFare: 35, peakSurge: 1.10, shareable: false, url: "https://www.rapido.bike", note: "Carries 1 rider; cannot be split." },
    { provider: "Ola", vehicle: "Mini cab", base: 50, perKm: 12.0, perMin: 1.2, minFare: 90, peakSurge: 1.25, shareable: true, url: "https://www.olacabs.com", note: "Seats up to 4; fare is split equally." },
    { provider: "Uber", vehicle: "Go cab", base: 45, perKm: 12.5, perMin: 1.3, minFare: 90, peakSurge: 1.30, shareable: true, url: "https://m.uber.com", note: "Seats up to 4; fare is split equally." },
    { provider: "inDrive", vehicle: "Cab", base: 40, perKm: 10.5, perMin: 1.0, minFare: 80, peakSurge: 1.00, shareable: true, url: "https://indrive.com", note: "Bid-based starting offer; fare is split." }
  ];

  // Popular preset locations in Delhi NCR for quick 1-click testing
  const PRESETS = [
    { name: "Knowledge Park II, Greater Noida", lat: 28.4601, lng: 77.4842 },
    { name: "India Gate, New Delhi", lat: 28.6129, lng: 77.2295 },
    { name: "Connaught Place, New Delhi", lat: 28.6304, lng: 77.2177 },
    { name: "Cyber Hub, Gurugram", lat: 28.4952, lng: 77.0892 },
    { name: "IGI Airport T3, New Delhi", lat: 28.5562, lng: 77.1000 },
    { name: "Noida Sector 62", lat: 28.6280, lng: 77.3649 }
  ];

  // --- App State ---
  const state = {
    user: { id: 1, name: "Alex Sharma", email: "alex@example.com", phone: "+91 98765 43210" },
    currentRide: null,
    matches: [],
    quotes: [],
    selectedQuote: null,
    currentBooking: null,
    activeChatUser: null,
    chatDraft: "",
    unreadMessages: 0,
    searchInterval: null,
    searchMap: null,
    requestMap: null,
    trips: [],
    conversations: {},
    ratings: {}
  };

  // Helper DOM selector
  const $ = (id) => document.getElementById(id);

  // --- LocalStorage persistence for Trips & Chats ---
  function loadPersistedData() {
    try {
      const savedTrips = localStorage.getItem("ridepool_trips");
      if (savedTrips) state.trips = JSON.parse(savedTrips);

      const savedChats = localStorage.getItem("ridepool_chats");
      if (savedChats) state.conversations = JSON.parse(savedChats);

      const savedUser = localStorage.getItem("ridepool_user");
      if (savedUser) state.user = JSON.parse(savedUser);
    } catch (e) {
      console.warn("Storage access failed", e);
    }
  }

  function saveTrips() {
    try {
      localStorage.setItem("ridepool_trips", JSON.stringify(state.trips));
    } catch (e) {}
  }

  function saveChats() {
    try {
      localStorage.setItem("ridepool_chats", JSON.stringify(state.conversations));
    } catch (e) {}
  }

  // --- Toast Notifications ---
  function showToast(msg) {
    let t = $("toast");
    if (!t) {
      t = document.createElement("div");
      t.id = "toast";
      t.className = "toast";
      document.body.appendChild(t);
    }
    t.textContent = msg;
    t.classList.add("show");
    setTimeout(() => t.classList.remove("show"), 2800);
  }

  // --- Geo & Math Helpers ---
  function haversineKm(lat1, lon1, lat2, lon2) {
    const R = 6371.0;
    const dLat = ((lat2 - lat1) * Math.PI) / 180;
    const dLon = ((lon2 - lon1) * Math.PI) / 180;
    const a =
      Math.sin(dLat / 2) * Math.sin(dLat / 2) +
      Math.cos((lat1 * Math.PI) / 180) *
        Math.cos((lat2 * Math.PI) / 180) *
        Math.sin(dLon / 2) *
        Math.sin(dLon / 2);
    return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  }

  function calcRoadDistance(lat1, lon1, lat2, lon2) {
    const km = haversineKm(lat1, lon1, lat2, lon2) * 1.3;
    return Math.max(km, 0.5);
  }

  function calcDurationMinutes(roadKm) {
    return Math.max(Math.round((roadKm / 28) * 60), 3);
  }

  function isPeak(dateObj) {
    const h = dateObj.getHours();
    return (h >= 8 && h < 10) || (h >= 17 && h < 20);
  }

  function calcQuotes(km, minutes, departureDate, groupSize) {
    const peak = isPeak(departureDate);
    const list = RATES.map((r) => {
      const raw = Math.max(r.minFare, r.base + r.perKm * km + r.perMin * minutes);
      const surge = peak ? r.peakSurge : 1.0;
      const total = Math.round(raw * surge);
      const sharers = r.shareable ? Math.min(Math.max(groupSize, 1), 4) : 1;
      const perPerson = Math.round(total / sharers);
      return {
        provider: r.provider,
        vehicle: r.vehicle,
        totalFare: total,
        perPerson: perPerson,
        shareable: r.shareable,
        isSurge: peak && r.peakSurge > 1.0,
        note: r.note,
        url: r.url,
        best: false
      };
    });

    list.sort((a, b) => a.perPerson - b.perPerson);
    if (list.length) list[0].best = true;
    return list;
  }

  function formatDate(d) {
    if (!d) return "";
    return new Date(d).toLocaleDateString("en-IN", {
      day: "2-digit",
      month: "short",
      hour: "2-digit",
      minute: "2-digit"
    });
  }

  function formatTime(d) {
    if (!d) return "";
    return new Date(d).toLocaleTimeString("en-IN", {
      hour: "2-digit",
      minute: "2-digit"
    });
  }

  // --- Router & View Management ---
  function showView(viewId) {
    document.querySelectorAll(".view-panel").forEach((el) => (el.hidden = true));
    document.querySelectorAll(".nav-link").forEach((el) => el.classList.remove("active"));

    const target = $("view-" + viewId);
    if (target) {
      target.hidden = false;
      window.scrollTo(0, 0);
    }

    const navLink = $("nav-" + viewId);
    if (navLink) navLink.classList.add("active");

    if (viewId === "request") {
      setTimeout(() => {
        if (state.requestMap) state.requestMap.invalidateSize();
      }, 150);
    } else if (viewId === "searching") {
      setTimeout(() => {
        if (state.searchMap) state.searchMap.invalidateSize();
      }, 150);
    }
  }

  function navigateTo(hash) {
    window.location.hash = hash;
  }

  function handleHashChange() {
    const hash = window.location.hash.replace("#", "") || "request";
    const [view, param] = hash.split("/");

    if (view === "searching") {
      if (!state.currentRide) return navigateTo("request");
      renderSearchingView();
      showView("searching");
    } else if (view === "results") {
      if (!state.currentRide) return navigateTo("request");
      renderResultsView();
      showView("results");
    } else if (view === "booking") {
      if (!state.currentBooking) return navigateTo("results");
      renderBookingView();
      showView("booking");
    } else if (view === "chat") {
      const riderId = parseInt(param, 10);
      renderChatView(riderId);
      showView("chat");
    } else if (view === "messages") {
      renderMessagesView();
      showView("messages");
    } else if (view === "trips") {
      renderTripsView();
      showView("trips");
    } else {
      showView("request");
    }
  }

  // --- MAP ICONS ---
  function createPinIcon(cls) {
    return L.divIcon({
      className: "",
      html: `<div class="pin ${cls}"></div>`,
      iconSize: [18, 18],
      iconAnchor: [9, 9]
    });
  }

  function createPulseIcon() {
    return L.divIcon({
      className: "",
      html: `<div class="pulse-marker"><div class="ring"></div><div class="dot"></div></div>`,
      iconSize: [18, 18],
      iconAnchor: [9, 9]
    });
  }

  // --- VIEW 1: REQUEST PAGE ---
  let reqPickupMarker = null;
  let reqDropMarker = null;
  let reqRouteLine = null;
  let activeField = "from";
  const searchTimers = {};

  function initRequestPage() {
    const departureInput = $("departure");
    if (departureInput) {
      const now = new Date(Date.now() + 30 * 60000);
      now.setMinutes(now.getMinutes() - now.getTimezoneOffset());
      departureInput.value = now.toISOString().slice(0, 16);
    }

    // Default form values
    $("from").value = PRESETS[0].name;
    $("fromLat").value = PRESETS[0].lat;
    $("fromLng").value = PRESETS[0].lng;

    $("to").value = PRESETS[1].name;
    $("toLat").value = PRESETS[1].lat;
    $("toLng").value = PRESETS[1].lng;

    // Initialize Request Map
    state.requestMap = L.map("map").setView(DEFAULT_CENTER, 10);
    L.tileLayer("https://tile.openstreetmap.org/{z}/{x}/{y}.png", {
      maxZoom: 19,
      attribution: "&copy; OpenStreetMap contributors"
    }).addTo(state.requestMap);

    updateRequestMapPoints();

    // Map Click handler to set pickup/drop
    state.requestMap.on("click", function (e) {
      const key = !$("fromLat").value ? "from" : !$("toLat").value ? "to" : activeField;
      const lat = e.latlng.lat;
      const lng = e.latlng.lng;

      setPlace(key, lat, lng, "Looking up address...", false);

      fetch(`${NOMINATIM}/reverse?format=jsonv2&lat=${lat}&lon=${lng}`)
        .then((r) => r.json())
        .then((d) => {
          const addr = d.display_name || `${lat.toFixed(4)}, ${lng.toFixed(4)}`;
          setPlace(key, lat, lng, addr, false);
        })
        .catch(() => {
          setPlace(key, lat, lng, `${lat.toFixed(4)}, ${lng.toFixed(4)}`, false);
        });

      $("map-hint").textContent =
        $("fromLat").value && $("toLat").value
          ? `Tap map again to move ${key === "from" ? "pickup" : "drop"} point.`
          : "Now tap the map to set your drop location.";
    });

    // Form inputs and debounce autocomplete
    ["from", "to"].forEach((key) => {
      const input = $(key);
      const list = $(key + "-list");

      input.addEventListener("focus", () => (activeField = key));
      input.addEventListener("input", () => {
        $(key + "Lat").value = "";
        $(key + "Lng").value = "";
        clearTimeout(searchTimers[key]);
        searchTimers[key] = setTimeout(() => searchNominatim(key), 550);
      });
      input.addEventListener("blur", () => {
        setTimeout(() => (list.hidden = true), 200);
      });
    });

    // Preset pills click
    document.querySelectorAll(".preset-pill").forEach((pill) => {
      pill.addEventListener("click", () => {
        const target = pill.dataset.target;
        const idx = parseInt(pill.dataset.index, 10);
        const preset = PRESETS[idx];
        if (preset) {
          setPlace(target, preset.lat, preset.lng, preset.name, true);
        }
      });
    });

    // Use current location button
    $("use-location").addEventListener("click", () => {
      if (!navigator.geolocation) {
        showToast("Geolocation is not supported by your browser");
        return;
      }
      $("use-location").textContent = "Locating you...";
      navigator.geolocation.getCurrentPosition(
        (pos) => {
          $("use-location").textContent = "Use my current location";
          const lat = pos.coords.latitude;
          const lng = pos.coords.longitude;
          fetch(`${NOMINATIM}/reverse?format=jsonv2&lat=${lat}&lon=${lng}`)
            .then((r) => r.json())
            .then((d) => {
              setPlace("from", lat, lng, d.display_name || "My Current Location", true);
              showToast("Pickup set to your current location");
            })
            .catch(() => {
              setPlace("from", lat, lng, "My Location", true);
            });
        },
        () => {
          $("use-location").textContent = "Use my current location";
          showToast("Could not access location. Please tap the map.");
        }
      );
    });

    // Submit ride request
    $("ride-form").addEventListener("submit", (e) => {
      e.preventDefault();
      const fromName = $("from").value.trim();
      const toName = $("to").value.trim();
      const fromLat = parseFloat($("fromLat").value);
      const fromLng = parseFloat($("fromLng").value);
      const toLat = parseFloat($("toLat").value);
      const toLng = parseFloat($("toLng").value);
      const departureVal = $("departure").value;

      if (!fromName || isNaN(fromLat) || !toName || isNaN(toLat) || !departureVal) {
        $("form-error-js").textContent = "Please select valid pickup and drop points.";
        $("form-error-js").hidden = false;
        return;
      }
      $("form-error-js").hidden = true;

      const departureDate = new Date(departureVal);
      const km = calcRoadDistance(fromLat, fromLng, toLat, toLng);
      const minutes = calcDurationMinutes(km);
      const quotes = calcQuotes(km, minutes, departureDate, 1);
      const minCost = Math.min(...quotes.map((q) => q.totalFare));
      const maxCost = Math.max(...quotes.map((q) => q.totalFare));

      state.currentRide = {
        id: Date.now(),
        fromName,
        fromLat,
        fromLng,
        toName,
        toLat,
        toLng,
        departureTime: departureDate,
        km,
        minutes,
        minCost,
        maxCost,
        peak: isPeak(departureDate),
        createdAt: new Date()
      };

      state.matches = []; // Reset co-riders
      navigateTo("searching");
    });
  }

  function setPlace(key, lat, lng, label, fit) {
    $(key).value = label;
    $(key + "Lat").value = lat;
    $(key + "Lng").value = lng;
    $(key + "-list").hidden = true;
    updateRequestMapPoints(fit);
  }

  function updateRequestMapPoints(fit = true) {
    const fromLat = parseFloat($("fromLat").value);
    const fromLng = parseFloat($("fromLng").value);
    const toLat = parseFloat($("toLat").value);
    const toLng = parseFloat($("toLng").value);

    if (reqPickupMarker) state.requestMap.removeLayer(reqPickupMarker);
    if (reqDropMarker) state.requestMap.removeLayer(reqDropMarker);
    if (reqRouteLine) state.requestMap.removeLayer(reqRouteLine);

    const pts = [];
    if (!isNaN(fromLat) && !isNaN(fromLng)) {
      reqPickupMarker = L.marker([fromLat, fromLng], { icon: createPinIcon("rider") })
        .addTo(state.requestMap)
        .bindTooltip("Pickup");
      pts.push([fromLat, fromLng]);
    }
    if (!isNaN(toLat) && !isNaN(toLng)) {
      reqDropMarker = L.marker([toLat, toLng], { icon: createPinIcon("drop") })
        .addTo(state.requestMap)
        .bindTooltip("Drop");
      pts.push([toLat, toLng]);
    }

    if (pts.length === 2) {
      reqRouteLine = L.polyline(pts, { color: "#0d6b57", weight: 3, dashArray: "6 8" }).addTo(state.requestMap);
      if (fit) state.requestMap.fitBounds(L.latLngBounds(pts), { padding: [50, 50] });

      // Try OSRM driving route
      fetch(`https://router.project-osrm.org/route/v1/driving/${fromLng},${fromLat};${toLng},${toLat}?overview=full&geometries=geojson`)
        .then((r) => r.json())
        .then((d) => {
          if (d.routes && d.routes.length) {
            const coords = d.routes[0].geometry.coordinates.map((c) => [c[1], c[0]]);
            if (reqRouteLine) state.requestMap.removeLayer(reqRouteLine);
            reqRouteLine = L.polyline(coords, { color: "#0d6b57", weight: 4 }).addTo(state.requestMap);
            if (fit) state.requestMap.fitBounds(reqRouteLine.getBounds(), { padding: [50, 50] });
          }
        })
        .catch(() => {});
    } else if (pts.length === 1 && fit) {
      state.requestMap.setView(pts[0], 13);
    }
  }

  function searchNominatim(key) {
    const q = $(key).value.trim();
    const list = $(key + "-list");
    if (q.length < 3) {
      list.hidden = true;
      return;
    }

    fetch(`${NOMINATIM}/search?format=jsonv2&limit=5&countrycodes=in&q=${encodeURIComponent(q)}`)
      .then((r) => r.json())
      .then((data) => {
        list.innerHTML = "";
        if (!data || !data.length) {
          list.hidden = true;
          return;
        }
        data.forEach((place) => {
          const li = document.createElement("li");
          li.textContent = place.display_name;
          li.addEventListener("mousedown", (e) => {
            e.preventDefault();
            setPlace(key, parseFloat(place.lat), parseFloat(place.lon), place.display_name, true);
          });
          list.appendChild(li);
        });
        list.hidden = false;
      })
      .catch(() => (list.hidden = true));
  }

  // --- VIEW 2: SEARCHING SCREEN ---
  let searchRiderLayer = null;

  function renderSearchingView() {
    const ride = state.currentRide;
    $("search-from").textContent = ride.fromName;
    $("search-to").textContent = ride.toName;
    $("search-time").textContent = `Leaving ${formatDate(ride.departureTime)}`;
    $("search-km").textContent = `${ride.km.toFixed(1)} km`;
    $("search-min").textContent = `${ride.minutes} min`;
    $("search-cost").textContent = `₹${ride.minCost} to ₹${ride.maxCost}`;

    $("search-peak-hint").hidden = !ride.peak;

    const demoBtn = $("demo-btn");
    demoBtn.disabled = false;
    demoBtn.textContent = "Add demo riders nearby";

    // Setup map
    if (state.searchMap) {
      state.searchMap.remove();
    }
    state.searchMap = L.map("search-map").setView([ride.fromLat, ride.fromLng], 12);
    L.tileLayer("https://tile.openstreetmap.org/{z}/{x}/{y}.png", {
      maxZoom: 19,
      attribution: "&copy; OpenStreetMap"
    }).addTo(state.searchMap);

    const fromPt = [ride.fromLat, ride.fromLng];
    const toPt = [ride.toLat, ride.toLng];

    L.marker(fromPt, { icon: createPulseIcon() }).addTo(state.searchMap).bindTooltip("You are here");
    L.marker(toPt, { icon: createPinIcon("drop") }).addTo(state.searchMap).bindTooltip("Drop");

    const straight = L.polyline([fromPt, toPt], { color: "#0d6b57", weight: 3, dashArray: "6 8" }).addTo(state.searchMap);
    state.searchMap.fitBounds(L.latLngBounds([fromPt, toPt]), { padding: [50, 50] });

    fetch(`https://router.project-osrm.org/route/v1/driving/${ride.fromLng},${ride.fromLat};${ride.toLng},${ride.toLat}?overview=full&geometries=geojson`)
      .then((r) => r.json())
      .then((d) => {
        if (d.routes && d.routes.length) {
          const coords = d.routes[0].geometry.coordinates.map((c) => [c[1], c[0]]);
          state.searchMap.removeLayer(straight);
          const line = L.polyline(coords, { color: "#0d6b57", weight: 4 }).addTo(state.searchMap);
          state.searchMap.fitBounds(line.getBounds(), { padding: [50, 50] });
        }
      })
      .catch(() => {});

    searchRiderLayer = L.layerGroup().addTo(state.searchMap);

    // If matches already exist, render them
    if (state.matches.length) {
      renderSearchRidersOnMap();
    }
  }

  function addDemoRiders() {
    const ride = state.currentRide;
    const count = 3;
    const newMatches = [];

    for (let i = 0; i < count; i++) {
      const jitterLat = (Math.random() - 0.5) * 0.012; // ~600m
      const jitterLng = (Math.random() - 0.5) * 0.012;
      const pickupKm = Math.max((Math.hypot(jitterLat, jitterLng) * 111).toFixed(1), 0.3);
      const minutesApart = Math.floor(Math.random() * 8) - 4;
      const dep = new Date(ride.departureTime.getTime() + minutesApart * 60000);

      const rider = {
        id: 100 + i + 1,
        name: `${DEMO_NAMES[i]} (demo)`,
        email: `demo-${DEMO_NAMES[i].toLowerCase()}@ridepool.demo`,
        phone: `+91 9810${i + 1} 4567${i}`,
        rating: DEMO_STARS[i],
        ratingCount: 3 + i,
        pickupKm: pickupKm,
        lat: ride.fromLat + jitterLat,
        lng: ride.fromLng + jitterLng,
        departureTime: dep,
        minutesApart: Math.abs(minutesApart),
        myStars: state.ratings[100 + i + 1] || null
      };
      newMatches.push(rider);
    }

    state.matches = newMatches;
    renderSearchRidersOnMap();

    $("status-title").textContent = `${newMatches.length} riders found!`;
    $("status-text").textContent = "Taking you to their details and fare comparison...";

    showToast("3 matching co-riders found nearby!");

    setTimeout(() => {
      navigateTo("results");
    }, 2000);
  }

  function renderSearchRidersOnMap() {
    if (!searchRiderLayer || !state.searchMap) return;
    searchRiderLayer.clearLayers();
    state.matches.forEach((r) => {
      const popup = document.createElement("div");
      popup.innerHTML = `<strong>${r.name}</strong><br>${r.pickupKm} km from you • ★ ${r.rating}`;
      L.marker([r.lat, r.lng], { icon: createPinIcon("rider") })
        .addTo(searchRiderLayer)
        .bindPopup(popup);
    });
  }

  // --- VIEW 3: RESULTS & FARES ---
  function renderResultsView() {
    const ride = state.currentRide;
    const groupSize = 1 + state.matches.length; // You + matches
    const quotes = calcQuotes(ride.km, ride.minutes, ride.departureTime, groupSize);
    state.quotes = quotes;
    state.selectedQuote = quotes[0]; // best deal pre-selected

    $("res-from").textContent = ride.fromName;
    $("res-to").textContent = ride.toName;
    $("res-summary").textContent = `${formatDate(ride.departureTime)}, about ${ride.km.toFixed(1)} km, around ${ride.minutes} min`;

    // Best Deal Card
    const best = quotes[0];
    const soloQuotes = calcQuotes(ride.km, ride.minutes, ride.departureTime, 1);
    const soloBest = soloQuotes[0].perPerson;
    const saving = soloBest - best.perPerson;

    if (state.matches.length > 0) {
      $("best-headline").textContent = `Best deal when you share with ${state.matches.length} rider${state.matches.length > 1 ? "s" : ""}`;
      $("best-sub").textContent = saving > 0 
        ? `You save about ₹${saving} compared with riding alone (₹${soloBest}).`
        : `Cheapest route split across your group.`;
      $("best-share-label").textContent = best.shareable ? "your share" : "for the ride";
    } else {
      $("best-headline").textContent = `No co-riders yet. This is the best solo deal.`;
      $("best-sub").textContent = `Riding alone, this is the cheapest option. You can keep searching anytime.`;
      $("best-share-label").textContent = "for the ride";
    }

    $("best-provider").textContent = `${best.provider} ${best.vehicle}`;
    $("best-price").textContent = best.perPerson;

    // Riders Section
    const ridersContainer = $("riders-list");
    ridersContainer.innerHTML = "";

    if (state.matches.length === 0) {
      $("riders-empty").hidden = false;
    } else {
      $("riders-empty").hidden = true;
      state.matches.forEach((m) => {
        const card = document.createElement("article");
        card.className = "rider";
        card.innerHTML = `
          <div class="rider-head">
            <h3 style="margin:0">${m.name}</h3>
            <span class="rating">
              <span class="star">★</span> ${m.rating.toFixed(1)}
              <span class="muted small">(${m.ratingCount})</span>
            </span>
          </div>
          <p class="muted small" style="margin:.25rem 0 0">
            Pickup ${m.pickupKm} km from yours, leaving ${formatTime(m.departureTime)} 
            (${m.minutesApart === 0 ? "same time" : m.minutesApart + " min apart"})
          </p>
          <div class="contact">
            Phone: <a href="tel:${m.phone}">${m.phone}</a><br>
            Email: <a href="mailto:${m.email}">${m.email}</a>
          </div>
          <div style="margin:.35rem 0 .5rem">
            <button type="button" class="btn small msg-btn" data-id="${m.id}">Message</button>
          </div>
          <div class="rate-form">
            <label class="small">Your rating:</label>
            <select class="rate-select" data-id="${m.id}">
              <option value="5" ${m.myStars === 5 ? "selected" : ""}>5 - Excellent</option>
              <option value="4" ${m.myStars === 4 ? "selected" : ""}>4 - Good</option>
              <option value="3" ${m.myStars === 3 ? "selected" : ""}>3 - Average</option>
              <option value="2" ${m.myStars === 2 ? "selected" : ""}>2 - Poor</option>
              <option value="1" ${m.myStars === 1 ? "selected" : ""}>1 - Terrible</option>
            </select>
            <button type="button" class="btn ghost small save-rate-btn" data-id="${m.id}">Save</button>
          </div>
        `;

        card.querySelector(".msg-btn").addEventListener("click", () => {
          navigateTo(`chat/${m.id}`);
        });

        card.querySelector(".save-rate-btn").addEventListener("click", (e) => {
          const stars = parseInt(card.querySelector(".rate-select").value, 10);
          state.ratings[m.id] = stars;
          m.myStars = stars;
          showToast(`Saved ${stars}-star rating for ${m.name}`);
        });

        ridersContainer.appendChild(card);
      });
    }

    // Fares Table
    const faresTbody = $("fares-tbody");
    faresTbody.innerHTML = "";

    quotes.forEach((q, idx) => {
      const tr = document.createElement("tr");
      if (q.best) tr.classList.add("selected");

      const surgeTag = q.isSurge ? `<span class="tag surge">Peak surge</span>` : "";
      const bestTag = q.best ? `<span class="tag best-deal">Best deal</span>` : "";

      tr.innerHTML = `
        <td>
          <label class="choice">
            <input type="radio" name="providerOption" value="${idx}" ${q.best ? "checked" : ""}>
            <div>
              <strong>${q.provider}</strong> ${q.vehicle}
              ${bestTag} ${surgeTag}
              <div class="muted small">${q.note}</div>
            </div>
          </label>
        </td>
        <td class="num">₹${q.totalFare}</td>
        <td class="num"><strong>₹${q.perPerson}</strong></td>
      `;

      tr.querySelector("input").addEventListener("change", () => {
        document.querySelectorAll("#fares-tbody tr").forEach((r) => r.classList.remove("selected"));
        tr.classList.add("selected");
        state.selectedQuote = q;
        updateSelectionText();
      });

      faresTbody.appendChild(tr);
    });

    updateSelectionText();
  }

  function updateSelectionText() {
    const q = state.selectedQuote;
    if (q) {
      $("selection-text").textContent = `Selected: ${q.provider} ${q.vehicle}, your share about ₹${q.perPerson}`;
    }
  }

  // --- VIEW 4: BOOKING SUMMARY ---
  function renderBookingView() {
    const b = state.currentBooking;
    const r = state.currentRide;

    $("book-saved-time").textContent = `Saved ${formatDate(b.createdAt)}`;
    $("book-provider").textContent = `${b.provider} ${b.vehicle}`;
    $("book-split-info").textContent =
      b.groupSize > 1
        ? `Shared by ${b.groupSize} riders. Total fare about ₹${b.totalFare}.`
        : `Riding alone. Total fare about ₹${b.totalFare}.`;
    $("book-share-price").textContent = b.perPerson;
    $("book-share-label").textContent = b.groupSize > 1 ? "your share" : "for the ride";

    $("book-provider-name-1").textContent = b.provider;
    $("book-open-btn").textContent = `Open ${b.provider}`;
    $("book-open-btn").href = b.url;

    $("book-pickup-text").textContent = r.fromName;
    $("book-drop-text").textContent = r.toName;
    $("book-leaving-text").textContent = formatDate(r.departureTime);

    // Co-riders summary
    const coridersContainer = $("book-coriders");
    coridersContainer.innerHTML = "";

    if (state.matches.length > 0) {
      state.matches.forEach((m) => {
        const item = document.createElement("article");
        item.className = "rider";
        item.innerHTML = `
          <div class="rider-head">
            <h3 style="margin:0">${m.name}</h3>
            <span class="rating"><span class="star">★</span> ${m.rating.toFixed(1)}</span>
          </div>
          <p class="muted small" style="margin:.25rem 0 .5rem">${m.phone}</p>
          <button type="button" class="btn small msg-booking-btn" data-id="${m.id}">Message about this booking</button>
        `;
        item.querySelector(".msg-booking-btn").addEventListener("click", () => {
          state.chatDraft = `Hey! I've booked our ride with ${b.provider} (${b.vehicle}). Total ₹${b.totalFare}, our share ₹${b.perPerson}. See you soon!`;
          navigateTo(`chat/${m.id}`);
        });
        coridersContainer.appendChild(item);
      });
    } else {
      coridersContainer.innerHTML = `<p class="muted">No co-riders on this booking. You can still invite friends or ride solo.</p>`;
    }
  }

  // --- VIEW 5: IN-APP CHAT ---
  function renderChatView(riderId) {
    const rider = state.matches.find((m) => m.id === riderId) || {
      id: riderId,
      name: "Co-Rider",
      phone: "+91 98765 00000"
    };

    state.activeChatUser = rider;
    $("chat-rider-name").textContent = rider.name;
    $("chat-rider-phone").textContent = rider.phone;
    $("chat-rider-phone").href = `tel:${rider.phone}`;

    const chatLog = $("chat-log");
    chatLog.innerHTML = "";

    if (!state.conversations[rider.id]) {
      state.conversations[rider.id] = [
        { mine: false, body: `Hey! I saw your ride request for ${state.currentRide ? state.currentRide.toName : "your destination"}. Are you still looking to pool?`, time: "Just now" }
      ];
    }

    state.conversations[rider.id].forEach((m) => addChatBubble(m));

    const input = $("chat-body");
    input.value = state.chatDraft || "";
    state.chatDraft = "";
    input.focus();
    chatLog.scrollTop = chatLog.scrollHeight;
  }

  function addChatBubble(m) {
    const log = $("chat-log");
    const div = document.createElement("div");
    div.className = `bubble ${m.mine ? "mine" : "theirs"}`;
    div.innerHTML = `
      <span class="body">${escapeHtml(m.body)}</span>
      <span class="time">${m.time}</span>
    `;
    log.appendChild(div);
    log.scrollTop = log.scrollHeight;
  }

  function sendChatMessage(text) {
    if (!text.trim() || !state.activeChatUser) return;
    const rider = state.activeChatUser;
    const nowTime = new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });

    const msg = { mine: true, body: text, time: nowTime };
    state.conversations[rider.id].push(msg);
    addChatBubble(msg);
    saveChats();

    // Automated Demo reply after 1.2s delay
    setTimeout(() => {
      const replyText = DEMO_REPLIES[Math.floor(Math.random() * DEMO_REPLIES.length)];
      const replyTime = new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
      const replyMsg = { mine: false, body: replyText, time: replyTime };

      if (!state.conversations[rider.id]) state.conversations[rider.id] = [];
      state.conversations[rider.id].push(replyMsg);
      saveChats();

      if (state.activeChatUser && state.activeChatUser.id === rider.id && !$("view-chat").hidden) {
        addChatBubble(replyMsg);
      } else {
        state.unreadMessages++;
        updateUnreadBadge();
        showToast(`New message from ${rider.name}`);
      }
    }, 1200);
  }

  function updateUnreadBadge() {
    const badge = $("unread-badge");
    if (state.unreadMessages > 0) {
      badge.textContent = state.unreadMessages;
      badge.hidden = false;
    } else {
      badge.hidden = true;
    }
  }

  // --- VIEW 6: MESSAGES INBOX ---
  function renderMessagesView() {
    const container = $("inbox-list");
    container.innerHTML = "";
    state.unreadMessages = 0;
    updateUnreadBadge();

    const ids = Object.keys(state.conversations);
    if (!ids.length) {
      $("inbox-empty").hidden = false;
      return;
    }
    $("inbox-empty").hidden = true;

    ids.forEach((id) => {
      const msgs = state.conversations[id];
      const last = msgs[msgs.length - 1];
      const rider = state.matches.find((m) => m.id == id) || { name: `Rider #${id}`, phone: "+91 00000 00000" };

      const item = document.createElement("a");
      item.className = "convo";
      item.href = `#chat/${id}`;
      item.innerHTML = `
        <div class="convo-main">
          <strong>${rider.name}</strong>
          <span class="convo-preview muted">${escapeHtml(last.body)}</span>
        </div>
        <div class="convo-meta">
          <span class="muted small">${last.time}</span>
        </div>
      `;
      container.appendChild(item);
    });
  }

  // --- VIEW 7: MY TRIPS ---
  function renderTripsView() {
    const container = $("trips-list");
    container.innerHTML = "";

    if (!state.trips.length) {
      $("trips-empty").hidden = false;
      return;
    }
    $("trips-empty").hidden = true;

    state.trips.forEach((t) => {
      const card = document.createElement("article");
      card.className = "trip";
      const hasBooking = !!t.booking;
      card.innerHTML = `
        <div class="trip-head">
          <div class="trip-route">
            <strong>${escapeHtml(t.fromName)}</strong>
            <span class="muted"> to </span>
            <strong>${escapeHtml(t.toName)}</strong>
          </div>
          <span class="tag ${hasBooking ? "" : "muted-tag"}">${hasBooking ? "Booked: " + t.booking.provider : "Planned"}</span>
        </div>
        <p class="muted small" style="margin:.35rem 0">Leaving ${formatDate(t.departureTime)}</p>
        ${
          hasBooking
            ? `<p class="small" style="margin:.25rem 0">
                ${t.booking.vehicle}, total about ₹${t.booking.totalFare} 
                (your share ₹${t.booking.perPerson} for ${t.booking.groupSize} riders)
              </p>`
            : `<p class="small muted" style="margin:.25rem 0">Estimated ₹${t.minCost} - ₹${t.maxCost}</p>`
        }
        <p style="margin:.6rem 0 0">
          <button type="button" class="btn small load-trip-btn" data-id="${t.id}">View details</button>
        </p>
      `;

      card.querySelector(".load-trip-btn").addEventListener("click", () => {
        state.currentRide = t;
        if (t.booking) {
          state.currentBooking = t.booking;
          navigateTo("booking");
        } else {
          navigateTo("results");
        }
      });

      container.appendChild(card);
    });
  }

  // --- Utility Escape ---
  function escapeHtml(str) {
    if (!str) return "";
    return str.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
  }

  // --- Global Event Listeners Initialization ---
  function initGlobalEvents() {
    window.addEventListener("hashchange", handleHashChange);

    // Searching Actions
    $("demo-btn").addEventListener("click", () => {
      $("demo-btn").disabled = true;
      $("demo-btn").textContent = "Adding nearby riders...";
      setTimeout(addDemoRiders, 800);
    });

    $("skip-search-btn").addEventListener("click", () => {
      navigateTo("results");
    });

    $("cancel-ride-btn").addEventListener("click", () => {
      state.currentRide = null;
      navigateTo("request");
      showToast("Ride cancelled");
    });

    // Results Actions
    $("book-form").addEventListener("submit", (e) => {
      e.preventDefault();
      const q = state.selectedQuote;
      if (!q) {
        showToast("Please choose a service first");
        return;
      }
      const booking = {
        id: Date.now(),
        provider: q.provider,
        vehicle: q.vehicle,
        totalFare: q.totalFare,
        perPerson: q.perPerson,
        groupSize: 1 + state.matches.length,
        url: q.url,
        createdAt: new Date()
      };

      state.currentBooking = booking;

      // Persist in trips
      const tripRecord = {
        ...state.currentRide,
        booking: booking
      };
      state.trips.unshift(tripRecord);
      saveTrips();

      showToast(`Selected ${q.provider} ${q.vehicle}!`);
      navigateTo("booking");
    });

    // Chat submit
    $("chat-form").addEventListener("submit", (e) => {
      e.preventDefault();
      const input = $("chat-body");
      const text = input.value.trim();
      if (text) {
        sendChatMessage(text);
        input.value = "";
      }
    });

    // Enter to send in chat textarea
    $("chat-body").addEventListener("keydown", (e) => {
      if (e.key === "Enter" && !e.shiftKey) {
        e.preventDefault();
        $("chat-form").requestSubmit();
      }
    });

    // Clipboard copy buttons
    document.querySelectorAll("[data-copy]").forEach((btn) => {
      btn.addEventListener("click", () => {
        const text = $(btn.dataset.copy).textContent.trim();
        if (navigator.clipboard) {
          navigator.clipboard.writeText(text).then(() => {
            const original = btn.textContent;
            btn.textContent = "Copied!";
            setTimeout(() => (btn.textContent = original), 1500);
            showToast(`Copied to clipboard: "${text}"`);
          });
        }
      });
    });

    // User profile click modal
    $("user-chip").addEventListener("click", () => {
      $("user-modal").hidden = false;
    });
    $("close-modal-btn").addEventListener("click", () => {
      $("user-modal").hidden = true;
    });
    $("switch-user-form").addEventListener("submit", (e) => {
      e.preventDefault();
      const name = $("modal-user-name").value.trim() || "Alex Sharma";
      state.user.name = name;
      $("user-name-display").textContent = name;
      localStorage.setItem("ridepool_user", JSON.stringify(state.user));
      $("user-modal").hidden = true;
      showToast(`Logged in as ${name}`);
    });
  }

  // --- Bootloader ---
  document.addEventListener("DOMContentLoaded", () => {
    loadPersistedData();
    $("user-name-display").textContent = state.user.name;
    $("modal-user-name").value = state.user.name;
    initRequestPage();
    initGlobalEvents();
    handleHashChange();
  });
})();
