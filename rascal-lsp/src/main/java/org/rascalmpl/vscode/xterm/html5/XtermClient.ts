

import { Terminal } from 'xterm';
import { AttachAddon } from 'xterm-addon-attach';

const term = new Terminal({
      cursorBlink: true,
      rows: 20
    });

term.setOption('cursorBlink', true);
term.setOption('cursorStyle', 'block');
term.setOption('convertEol', true);



const urlParams = new URLSearchParams(window.location.search)
const socket = new WebSocket('ws://localhost:' + window.location.port + '/?project=' + urlParams.get('project'));
const attachAddon = new AttachAddon(socket);
term.loadAddon(attachAddon);

term.open(document.getElementById('xterm-container'));

var viewport = document.querySelector('.xterm-viewport');

/* TODO: get viewport dimensions from URL parameters */
