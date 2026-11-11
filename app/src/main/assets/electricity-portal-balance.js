(function () {
    if (location.origin !== 'https://ecard.sit.edu.cn'
        || !/^\/epeortal\/pages\/h5\/elecCharge\/?$/.test(location.pathname)) return '';

    if (__ELECTRICITY_CHOOSE__ && !document.getElementById('xinxin-electricity-read-only')) {
        const style = document.createElement('style');
        style.id = 'xinxin-electricity-read-only';
        style.textContent = '.elecCharge > :not(:first-child):not(.elecCharge-back):not(.elecCharge-toast) { display: none !important; }';
        document.head.appendChild(style);
    }

    const page = document.querySelector('.elecCharge');
    if (!page) return 'pending';
    // The school view renders 0 before the room request has returned.
    let component = page.__vue__;
    while (component && typeof component.initRoomInfo !== 'function') component = component.$parent;
    if (!component || component.disabled !== false) return 'pending';

    const room = component.room;
    const roomName = component.roomName || (room && room.roomName);
    if (!room || !roomName || room.restElecDegree === null || room.restElecDegree === undefined) return 'pending';
    return JSON.stringify({
        name: roomName,
        roomId: room.roomId,
        buildingId: room.buiId,
        value: room.restElecDegree
    });
})();
