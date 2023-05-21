window.onload = function() {
  document.getElementById("webhookUrl").value = "http://" + window.location.host + "/webhook/plex";
};


function copy() {

  // Get the text field
  var copyText = document.getElementById("webhookUrl");

  // Select the text field
  copyText.select();
  copyText.setSelectionRange(0, 99999); // For mobile devices

   // Copy the text inside the text field
  navigator.clipboard.writeText(copyText.value);

  // Alert the copied text
  document.getElementById("copyBtn").innerHTML = "Copied";
}