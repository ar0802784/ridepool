/* Results page: shows which service is selected next to the Book now button. */
(function () {
  var form = document.getElementById("book-form");
  var text = document.getElementById("selection-text");
  if (!form || !text) { return; }

  function update() {
    var picked = form.querySelector('input[name="provider"]:checked');
    text.textContent = picked
      ? "Selected: " + picked.dataset.label + ", your share about Rs " + picked.dataset.share
      : "Select a service to book.";
  }

  form.addEventListener("change", update);
  update();
})();
