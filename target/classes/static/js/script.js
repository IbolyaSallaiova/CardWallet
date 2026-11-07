document.addEventListener("DOMContentLoaded", function () {
    setupCardDetail();
    setupSearch();
    setupAddCardModal();
});

function setupAddCardModal() {
    const trigger = document.getElementById("add-card-tile");
    const overlay = document.getElementById("add-card-overlay");
    const closeBtn = document.getElementById("add-card-close");
    const cancelBtn = document.getElementById("add-card-cancel");
    if (!trigger || !overlay) return;

    function openModal() {
        overlay.hidden = false;
        const firstInput = overlay.querySelector("input");
        if (firstInput) firstInput.focus();
    }

    function closeModal() {
        overlay.hidden = true;
    }

    trigger.addEventListener("click", openModal);
    trigger.addEventListener("keydown", function (event) {
        if (event.key === "Enter" || event.key === " ") {
            event.preventDefault();
            openModal();
        }
    });

    if (closeBtn) closeBtn.addEventListener("click", closeModal);
    if (cancelBtn) cancelBtn.addEventListener("click", closeModal);

    overlay.addEventListener("click", function (event) {
        if (event.target === overlay) {
            closeModal();
        }
    });

    document.addEventListener("keydown", function (event) {
        if (event.key === "Escape" && !overlay.hidden) {
            closeModal();
        }
    });
}

function setupCardDetail() {
    const dataEl = document.getElementById("cards-data");
    if (!dataEl) return;

    const cards = JSON.parse(dataEl.textContent || "[]");
    const cardsById = {};
    cards.forEach(function (card) {
        cardsById[card.id] = card;
    });

    const tiles = document.querySelectorAll(".card-tile");
    const detail = document.getElementById("card-detail");
    const closeBtn = document.getElementById("detail-close");
    const deleteBtn = document.getElementById("detail-delete");
    const deleteOverlay = document.getElementById("delete-confirm-overlay");
    const deleteForm = document.getElementById("delete-confirm-form");
    const deleteNameEl = document.getElementById("delete-confirm-name");
    const deleteCancelBtn = document.getElementById("delete-confirm-cancel");
    const deleteCloseBtn = document.getElementById("delete-confirm-close");
    let openCardId = null;

    function closeDetail() {
        detail.hidden = true;
        openCardId = null;
        tiles.forEach(function (tile) {
            tile.classList.remove("active");
        });
    }

    function openDetail(tile, card) {
        document.getElementById("detail-nickname").textContent = card.nickname;
        document.getElementById("detail-number").textContent = card.fullNumber;
        document.getElementById("detail-status").textContent = card.status;
        document.getElementById("detail-expiry").textContent = card.expiry;
        document.getElementById("detail-balance").textContent = card.balanceFormatted;

        const paymentsBody = document.getElementById("detail-payments");
        paymentsBody.innerHTML = "";
        card.payments.forEach(function (payment) {
            const tr = document.createElement("tr");
            [payment.date, payment.merchant, payment.category, payment.amountFormatted].forEach(function (text) {
                const td = document.createElement("td");
                td.textContent = text;
                tr.appendChild(td);
            });
            paymentsBody.appendChild(tr);
        });

        detail.hidden = false;
        tiles.forEach(function (t) {
            t.classList.toggle("active", t === tile);
        });
        openCardId = card.id;
    }

    tiles.forEach(function (tile) {
        tile.addEventListener("click", function () {
            const cardId = Number(tile.getAttribute("data-card-id"));
            if (openCardId === cardId) {
                closeDetail();
                return;
            }
            openDetail(tile, cardsById[cardId]);
        });
    });

    if (closeBtn) {
        closeBtn.addEventListener("click", closeDetail);
    }

    function closeDeleteConfirm() {
        if (deleteOverlay) deleteOverlay.hidden = true;
    }

    if (deleteBtn && deleteOverlay && deleteForm) {
        deleteBtn.addEventListener("click", function () {
            if (openCardId === null) return;
            const card = cardsById[openCardId];
            deleteNameEl.textContent = card.nickname;
            deleteForm.action = "/cards/" + card.id + "/delete";
            deleteOverlay.hidden = false;
        });

        if (deleteCancelBtn) deleteCancelBtn.addEventListener("click", closeDeleteConfirm);
        if (deleteCloseBtn) deleteCloseBtn.addEventListener("click", closeDeleteConfirm);

        deleteOverlay.addEventListener("click", function (event) {
            if (event.target === deleteOverlay) {
                closeDeleteConfirm();
            }
        });

        document.addEventListener("keydown", function (event) {
            if (event.key === "Escape" && !deleteOverlay.hidden) {
                closeDeleteConfirm();
            }
        });
    }
}

function setupSearch() {
    const input = document.getElementById("search-input");
    const button = document.getElementById("search-btn");
    const resultsBody = document.getElementById("results-body");
    const noResults = document.getElementById("no-results");

    if (!button) return;

    async function runSearch() {
        const term = input.value;

        const response = await fetch("/search", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ term: term }),
        });

        if (response.status === 302 || response.redirected) {
            window.location.href = "/login";
            return;
        }

        const data = await response.json();
        renderResults(data.results || []);
    }

    function renderResults(rows) {
        resultsBody.innerHTML = "";

        if (rows.length === 0) {
            noResults.style.display = "block";
            return;
        }

        noResults.style.display = "none";
        rows.forEach(function (row) {
            const tr = document.createElement("tr");
            row.forEach(function (cell) {
                const td = document.createElement("td");
                td.textContent = cell;
                tr.appendChild(td);
            });
            resultsBody.appendChild(tr);
        });
    }

    button.addEventListener("click", runSearch);
    input.addEventListener("keydown", function (event) {
        if (event.key === "Enter") {
            runSearch();
        }
    });
}
