function init() {
  rook_io.input_subscribe(null, handle_io_message);
  rook_io.output_subscribe(null, handle_io_message);
}

function handle_io_message(json) {
  handle_io_value(json.type, json.name, json.dataType, json.value);
}

function handle_io_value(type, name, dataType, value) {
  var table = $("#"+type);
  var rowId = type + "_" + name.replace(/ /g,'');
  if($("#"+rowId).length == 0) {
    template_create("template_row", rowId, table)
        .find('[name="key"]').html(name + ": ");
  }
  $("#"+rowId).find('[name="value"]').html(rook_io.stringify(value, dataType));
}