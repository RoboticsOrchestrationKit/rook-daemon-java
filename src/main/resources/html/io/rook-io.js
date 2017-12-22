var rook_io = {
//start rook_io

input_subscribe: function (name, response_handler) {
  if(name == null) {
    rook_io._send_listen_json('{ "type": "input_subscribe" }', response_handler);
  } else {
    rook_io._send_listen_json('{ "type": "input_subscribe", "name": "' + name + '" }', response_handler);
 }
},

output_subscribe: function(name, response_handler) {
  if(name == null) {
    rook_io._send_listen_json('{ "type": "output_subscribe" }', response_handler);
  } else {
    rook_io._send_listen_json('{ "type": "output_subscribe", "name": "' + name + '" }', response_handler);
 }
},

stringify: function (value, dataType) {
  switch(dataType) {
    case 'U8':
    case 'U16':
    case 'U32':
    case 'U64':
    case 'I8':
    case 'I16':
    case 'I32':
    case 'I64':
      return rook_io.bigint(value, dataType);
    case 'UTF8':
      return atob(value);
    default:
      return value;
  }
},

bigint: function (value, dataType) {
  if(dataType === 'U8' || dataType === 'U16' || dataType === 'U32' || dataType === 'U64') {
    return bigInt(rook_io._reverseHex(rook_io._base64ToHex(value)), 16).toString();
  } else if(dataType === 'I8' || dataType === 'I16' || dataType === 'I32' || dataType === 'I64') {
    var hex = rook_io._reverseHex(rook_io._base64ToHex(value));
    var negative = hex.charCodeAt(0) >= 56;
    if(negative) {
      return bigInt(bigInt(hex, 16).subtract(1).not()).times(-1).toString();
    } else {
      return bigInt(hex, 16).toString();
    }
  } else {
    throw "Bad dataType: " + dataType;
    return null;
  }
},

_send_listen_json: function (text, response_handler) {
  var ws = new WebSocket("ws://" + window.location.host + "/ws", "rook_io");
  ws.onmessage = function (evt)
  {
    if(response_handler != null) {
      var json = JSON.parse(evt.data);
      response_handler(json);
    }
  };
  ws.onopen = function()
  {
    ws.send(text);
  };
},

_hexToBase64: function (hexstring) {
    return btoa(hexstring.match(/\w{2}/g).map(function(a) {
        return String.fromCharCode(parseInt(a, 16));
    }).join(""));
},

_base64ToHex: function (base64) {
  var raw = atob(base64);
  var HEX = '';
  for ( i = 0; i < raw.length; i++ ) {
    var _hex = raw.charCodeAt(i).toString(16)
    HEX += (_hex.length==2?_hex:'0'+_hex);
  }
  return HEX.toUpperCase();
},

_reverseHex: function (hex) {
  var m = hex.match(/../g);
  m.reverse();  
  return m.join("");
}

//end rook_io
}