var autoRefreshIntervalId = null;
var vaccineCenterLeafletGroup = null;
var personLeafletGroup = null;
const dateTimeFormatter = JSJoda.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
const dateFormatter = JSJoda.DateTimeFormatter.ofPattern("yyyy-MM-dd")
const timeFormatter = JSJoda.DateTimeFormatter.ofPattern("HH:mm")

function refreshSolution() {
  $.getJSON("/vaccinationSchedule?page=0", function (schedule) {
    refreshSolvingButtons(schedule.solverStatus != null && schedule.solverStatus !== "NOT_SOLVING");
    $("#score").text("Score: " + (schedule.score == null ? "?" : schedule.score));

    const vaccineTypesDiv = $("#vaccineTypes");
    vaccineTypesDiv.children().remove();
    const vaccineTypeMap = new Map();
    schedule.vaccineTypes.forEach((vaccineType) => {
      const color = pickColor(vaccineType.name);
      vaccineTypesDiv.append($(`<div class="col"/>`).append($(`<div class="card" style="background-color: ${color}"/>`)
          .append($(`<div class="card-body p-2"/>`)
              .append($(`<h5 class="card-title mb-0"/>`).text(vaccineType.name)))));
      vaccineTypeMap.set(vaccineType.name, vaccineType);
    });

    const scheduleTable = $("#scheduleTable");
    scheduleTable.children().remove();
    vaccineCenterLeafletGroup.clearLayers();
    const unassignedPeronsDiv = $("#unassignedPersons");
    unassignedPeronsDiv.children().remove();

    if (schedule.appointments.size > 10000) {
      scheduleTable.append($(`<p/>`)
          .text("There are " + schedule.appointments.size + " appointments. Too much data to show a schedule."));
      return;
    }


    const vaccinationCenterMap = new Map(
        schedule.vaccinationCenters.map(vaccinationCenter => [vaccinationCenter.id, vaccinationCenter]));

    const dateTimeSet = new Set();
    const dateTimeList = [];
    const vaccinationCenterIdToBoothIdSetMap = new Map(
        schedule.vaccinationCenters.map(vaccinationCenter => [vaccinationCenter.id, new Set()]));
    schedule.appointments.forEach((appointment) => {
      const dateTime = JSJoda.LocalDateTime.parse(appointment.dateTime);
      const dateTimeString = dateTimeFormatter.format(dateTime)
      if (!dateTimeSet.has(dateTimeString)) {
        dateTimeSet.add(dateTimeString);
        dateTimeList.push(dateTime);
      }
      vaccinationCenterIdToBoothIdSetMap.get(appointment.vaccinationCenter).add(appointment.boothId);
    });
    dateTimeList.sort((a, b) => a.compareTo(b));

    const thead = $("<thead>").appendTo(scheduleTable);
    const headerRow = $("<tr>").appendTo(thead);
    headerRow.append($("<th>Time</th>"));
    schedule.vaccinationCenters.forEach((vaccinationCenter) => {
      const boothIdSet = vaccinationCenterIdToBoothIdSetMap.get(vaccinationCenter.id);
      boothIdSet.forEach((boothId) => {
        headerRow
            .append($("<th/>")
                .append($("<span/>").text(vaccinationCenter.name + (boothIdSet.size <= 1 ? "" : " booth " + boothId))));
      });
    });

    const appointmentMap = new Map(schedule.appointments
        .map(appointment => [JSJoda.LocalDateTime.parse(appointment.dateTime) + "/" + appointment.vaccinationCenter + "/" + appointment.boothId, appointment]));
    if (schedule.appointments.length !== appointmentMap.size) {
      var conflicts = schedule.appointments.length - appointmentMap.size;
      scheduleTable.append($(`<p class="badge badge-danger">There are ${conflicts} double bookings.</span>`));
    }
    const appointmentToPersonMap = new Map();
    schedule.people.forEach((person) => {
      if (person.appointment != null) {
        appointmentToPersonMap.set(JSJoda.LocalDateTime.parse(person.appointment.dateTime) + "/" + person.appointment.vaccinationCenter + "/" + person.appointment.boothId, person);
      }
    });

    const tbody = $(`<tbody>`).appendTo(scheduleTable);
    var previousDateTime = null;
    dateTimeList.forEach((dateTime) => {
      const row = $(`<tr>`).appendTo(tbody);
      var showDate = (previousDateTime == null || dateTime.toLocalDate().compareTo(previousDateTime.toLocalDate()) !== 0);
      row
          .append($(`<th class="align-middle"/>`)
              .append($(`<span style="float: right"/>`).text(showDate ? dateTimeFormatter.format(dateTime) : timeFormatter.format(dateTime))));
      previousDateTime = dateTime;
      schedule.vaccinationCenters.forEach((vaccinationCenter) => {
        const boothIdSet = vaccinationCenterIdToBoothIdSetMap.get(vaccinationCenter.id);
        boothIdSet.forEach((boothId) => {
          var appointment = appointmentMap.get(dateTime + "/" + vaccinationCenter.id + "/" + boothId);
          if (appointment == null) {
            row.append($(`<td class="p-1"/>`));
          } else {
            const color = pickColor(appointment.vaccineType);
            var cardBody = $(`<div class="card-body pt-1 pb-1 px-2"/>`);
            const person = appointmentToPersonMap.get(dateTime + "/" + vaccinationCenter.id + "/" + boothId);
            if (person == null) {
              cardBody.append($(`<h5 class="card-title mb-0"/>`).text("Unassigned"));
            } else {
              var appointmentDateTime = JSJoda.LocalDateTime.parse(appointment.dateTime);

              var birthdate = JSJoda.LocalDate.parse(person.birthdate);
              var age = birthdate.until(appointmentDateTime.toLocalDate(), JSJoda.ChronoUnit.YEARS);
              cardBody.append($(`<h5 class="card-title mb-1"/>`)
                  .text(person.name + " (" + age + ")"));
              const vaccineType = vaccineTypeMap.get(appointment.vaccineType);
              if (vaccineType.maximumAge != null && age > vaccineType.maximumAge) {
                cardBody.append($(`<p class="badge badge-danger mb-0"/>`).text(vaccineType.name + " maximum age is " + vaccineType.maximumAge));
              }
              if (person.requiredVaccineType != null
                  && appointment.vaccineType !== person.requiredVaccineType) {
                cardBody.append($(`<p class="badge badge-danger ms-2 mb-0"/>`).text("Required vaccine is " + person.requiredVaccineType));
              }
              if (person.preferredVaccineType != null
                  && appointment.vaccineType !== person.preferredVaccineType) {
                cardBody.append($(`<p class="badge badge-warning ms-2 mb-0"/>`).text("Preferred vaccine is " + person.preferredVaccineType));
              }
              if (person.requiredVaccinationCenter != null
                  && appointment.vaccinationCenter !== person.requiredVaccinationCenter) {
                const requiredVaccinationCenter = vaccinationCenterMap.get(person.requiredVaccinationCenter);
                cardBody.append($(`<p class="badge badge-danger ms-2 mb-0"/>`).text("Required vaccination center is " + requiredVaccinationCenter.name));
              }
              if (person.preferredVaccinationCenter != null
                  && appointment.vaccinationCenter !== person.preferredVaccinationCenter) {
                const preferredVaccinationCenter = vaccinationCenterMap.get(person.preferredVaccinationCenter);
                cardBody.append($(`<p class="badge badge-warning ms-2 mb-0"/>`).text("Preferred vaccination center is " + preferredVaccinationCenter.name));
              }
              if (person.readyDate != null) {
                var readyDate = JSJoda.LocalDate.parse(person.readyDate);
                var readyDateDiff = readyDate.until(appointmentDateTime.toLocalDate(), JSJoda.ChronoUnit.DAYS);
                if (readyDateDiff < 0) {
                  cardBody.append($(`<p class="badge badge-danger ms-2 mb-0"/>`).text("Dose is " + (-readyDateDiff) + " days too early"));
                }
              }
              if (person.dueDate != null) {
                var dueDate = JSJoda.LocalDate.parse(person.dueDate);
                var dueDateDiff = dueDate.until(appointmentDateTime.toLocalDate(), JSJoda.ChronoUnit.DAYS);

                if (dueDateDiff > 0) {
                  cardBody.append($(`<p class="badge badge-danger ms-2 mb-0"/>`).text("Dose is " + (dueDateDiff) + " days too late"));
                }
              }
              var dosePrefix = person.doseNumber.toString() + ((person.doseNumber === 1) ? "st" : "nd");
              var doseSuffix = "";
              if (person.idealDate != null) {
                var idealDate = JSJoda.LocalDate.parse(person.idealDate);
                var idealDateDiff =  idealDate.until(appointmentDateTime.toLocalDate(), JSJoda.ChronoUnit.DAYS);

                doseSuffix = " (" + (idealDateDiff === 0 ? "ideal day"
                    : (idealDateDiff < 0 ? (-idealDateDiff) + " days too early"
                        : idealDateDiff + " days too late")) + ")";
              }
              cardBody.append($(`<p class="card-text ms-2 mb-0"/>`).text(dosePrefix + " dose" + doseSuffix));
            }
            row.append($(`<td class="p-1"/>`)
                .append($(`<div class="card" style="background-color: ${color}"/>`)
                    .append(cardBody)));
          }
        });
      });
    });


    schedule.vaccinationCenters.forEach((vaccinationCenter) => {
      L.marker(vaccinationCenter.location).addTo(vaccineCenterLeafletGroup);
    });
    var assignedPersonCount = 0;
    var unassignedPersonCount = 0;
    personLeafletGroup.clearLayers();
    schedule.people.forEach((person) => {
      const appointment = person.appointment;
      const personColor = (appointment == null ? "gray" : pickColor(appointment.vaccineType));
      L.circleMarker(person.homeLocation, {radius: 4, color: personColor, weight: 2}).addTo(personLeafletGroup);
      if (person.requiredVaccineType != null) {
        const requiredVaccineTypeColor = pickColor(person.requiredVaccineType);
        L.circleMarker(person.homeLocation, {radius: 3, color: requiredVaccineTypeColor, weight: 0, fillOpacity: 1.0}).addTo(personLeafletGroup);
      }
      if (appointment != null) {
        assignedPersonCount++;
        const vaccinationCenter = vaccinationCenterMap.get(appointment.vaccinationCenter);
        L.polyline([person.homeLocation, vaccinationCenter.location], {color: personColor, weight: 1}).addTo(personLeafletGroup);
      } else {
        unassignedPersonCount++;
        var firstDateTime = dateTimeList[0];
        var birthdate = JSJoda.LocalDate.parse(person.birthdate);
        var age = birthdate.until(firstDateTime.toLocalDate(), JSJoda.ChronoUnit.YEARS);

        var dosePrefix = person.doseNumber.toString() + ((person.doseNumber === 1) ? "st" : "nd");
        var doseSuffix = "";
        if (person.requiredVaccineType != null) {
          const vaccineType = vaccineTypeMap.get(person.requiredVaccineType);
          doseSuffix += " " + vaccineType.name;
        }
        if (person.idealDate != null) {
          doseSuffix += " (ideally " + dateFormatter.format(JSJoda.LocalDate.parse(person.idealDate)) + ")";
        }
        unassignedPeronsDiv.append($(`<div class="col"/>`).append($(`<div class="card"/>`)
            .append($(`<div class="card-body pt-1 pb-1 px-2"/>`)
                .append($(`<h5 class="card-title mb-1"/>`).text(person.name + " (" + age + ")"))
                .append($(`<p class="card-text ms-2"/>`).text(dosePrefix + " dose" + doseSuffix)))));
      }
    });
    $("#assignedPersonCount").text(assignedPersonCount);
    $("#unassignedPersonCount").text(unassignedPersonCount);
  });
}

function solve() {
  $.post("/vaccinationSchedule/solve", function () {
    refreshSolvingButtons(true);
  }).fail(function (xhr, ajaxOptions, thrownError) {
    showError("Start solving failed.", xhr);
  });
}

function refreshSolvingButtons(solving) {
  if (solving) {
    $("#solveButton").hide();
    $("#stopSolvingButton").show();
    if (autoRefreshIntervalId == null) {
      autoRefreshIntervalId = setInterval(refreshSolution, 2000);
    }
  } else {
    $("#solveButton").show();
    $("#stopSolvingButton").hide();
    if (autoRefreshIntervalId != null) {
      clearInterval(autoRefreshIntervalId);
      autoRefreshIntervalId = null;
    }
  }
}

function stopSolving() {
  $.post("/vaccinationSchedule/stopSolving", function () {
    refreshSolvingButtons(false);
    refreshSolution();
  }).fail(function (xhr, ajaxOptions, thrownError) {
    showError("Stop solving failed.", xhr);
  });
}

$(document).ready(function () {
  replaceTimefoldAutoHeaderFooter();
  $.ajaxSetup({
    headers: {
      "Content-Type": "application/json",
      "Accept": "application/json"
    }
  });
  // Extend jQuery to support $.put() and $.delete()
  jQuery.each(["put", "delete"], function (i, method) {
    jQuery[method] = function (url, data, callback, type) {
      if (jQuery.isFunction(data)) {
        type = type || callback;
        callback = data;
        data = undefined;
      }
      return jQuery.ajax({
        url: url,
        type: method,
        dataType: type,
        data: data,
        success: callback
      });
    };
  });

  $("#refreshButton").click(function () {
    refreshSolution();
  });
  $("#solveButton").click(function () {
    solve();
  });
  $("#stopSolvingButton").click(function () {
    stopSolving();
  });

  const leafletMap = L.map("leafletMap", {doubleClickZoom: false})
      .setView([33.75, -84.40], 10);

  L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
    maxZoom: 19,
    attribution: '&copy; <a href="https://www.openstreetmap.org/">OpenStreetMap</a> contributors',
  }).addTo(leafletMap);
  $(`button[data-bs-toggle="tab"]`).on("shown.bs.tab", function (e) {
    leafletMap.invalidateSize();
  })

  vaccineCenterLeafletGroup = L.layerGroup();
  vaccineCenterLeafletGroup.addTo(leafletMap);
  personLeafletGroup = L.layerGroup();
  personLeafletGroup.addTo(leafletMap);

  refreshSolution();
});
