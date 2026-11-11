// Drive the school's own six cascading selectors. No guessed IDs or payment actions.
(function () {
    'use strict';
    var plan = __ELECTRICITY_ROOM_MATCH__;
    var path = location.pathname.replace(/\/$/, '');
    if (location.origin !== 'https://ecard.sit.edu.cn'
        || !/^\/epeortal\/pages\/h5\/elec(Query|Charge)$/.test(path)) return JSON.stringify({status: 'failed', reason: 'page'});
    if (!/^\d{1,4}$/.test(plan.building) || !/^\d{1,8}$/.test(plan.room))
        return JSON.stringify({status: 'failed', reason: 'input'});
    function component(selector, method) {
        var node = document.querySelector(selector);
        while (node) {
            var vm = node.__vue__;
            while (vm) { if (typeof vm[method] === 'function') return vm; vm = vm.$parent; }
            var instance = node.__vueParentComponent;
            while (instance) {
                var proxy = instance.proxy || instance.ctx;
                if (proxy && typeof proxy[method] === 'function') return proxy;
                instance = instance.parent;
            }
            node = node.parentElement;
        }
        return null;
    }
    var previous = window.__xinxinDormMatch;
    if (previous && previous.building === plan.building && previous.number === plan.room
        && previous.status !== 'opening') return JSON.stringify(previous);
    var page = component('.elecQuery', 'sysChange');
    if (!page) {
        var charge = component('.elecCharge', 'back');
        if (charge && (!previous || previous.status !== 'opening')) {
            window.__xinxinDormMatch = {status: 'opening', building: plan.building, number: plan.room};
            charge.back();
        }
        return JSON.stringify({status: 'pending'});
    }
    var state = {status: 'pending', building: plan.building, number: plan.room};
    window.__xinxinDormMatch = state;
    var deadline = Date.now() + 90000;
    function active() {
        return window.__xinxinDormMatch === state
            && location.pathname.replace(/\/$/, '') === '/epeortal/pages/h5/elecQuery';
    }
    function fail(reason) { if (active()) { state.status = 'failed'; state.reason = reason; } }
    function clean(value) { return String(value == null ? '' : value).replace(/\s+/g, '').trim(); }
    function unique(list, predicate) {
        var found = -1;
        for (var i = 0; i < list.length; i++) if (predicate(list[i])) {
            if (found >= 0) throw new Error('ambiguous');
            found = i;
        }
        return found;
    }
    function named(list, field, names) {
        return unique(list, function (item) { return names.indexOf(clean(item[field])) >= 0; });
    }
    function buildingNumber(name) {
        var match = /^(?:第)?0*(\d+)(?:号)?(?:楼|栋)?$/.exec(clean(name));
        return match ? String(Number(match[1])) : '';
    }
    function roomNumber(name) { return clean(name).replace(/^(?:寝室|房间)/, '').replace(/(?:室|号)$/, ''); }
    function floorNumber(name) {
        var match = /^(?:第)?0*(\d+)(?:楼|层|F)?$/i.exec(clean(name));
        return match ? String(Number(match[1])) : '';
    }
    function wait(field, pending, initial) {
        var until = Math.min(deadline, Date.now() + 12000);
        return new Promise(function (resolve, reject) {
            function tick() {
                if (!active()) { reject(new Error('cancelled')); return; }
                var list = page[field];
                // Each school change handler clears the child list synchronously,
                // then replaces it with the response array (including an empty array).
                if (Array.isArray(list) && (initial ? list.length > 0 : list !== pending)) { resolve(list); return; }
                if (Date.now() >= until) { reject(new Error('timeout')); return; }
                setTimeout(tick, 120);
            }
            tick();
        });
    }
    function select(method, index, child) {
        if (!active()) return Promise.reject(new Error('cancelled'));
        page[method]({detail: {value: index}});
        return child ? wait(child, page[child], false) : Promise.resolve();
    }
    function required(index, reason) { if (index < 0) throw new Error(reason); return index; }
    async function match() {
        var systems = await wait('sysList', null, true);
        var system = required(named(systems, 'elcname', ['新电费缴纳']), 'system');
        var areas = await select('sysChange', system, 'areaList');
        var area = required(named(areas, 'areaName', ['奉贤校区']), 'campus');
        var districts = await select('areaChange', area, 'distList');
        var district = required(named(districts, 'districtName', ['住宿区']), 'district');
        var buildings = await select('distChange', district, 'buildList');
        var building = required(unique(buildings, function (row) {
            return buildingNumber(row.buiName) === String(Number(plan.building));
        }), 'building');
        var floors = await select('buildChange', building, 'floorList');
        if (!floors.length || floors.length > 100) throw new Error('floor');
        // The number suggests a floor, but the actual room list must confirm it.
        var hint = plan.room.length > 2 ? String(Number(plan.room.slice(0, -2))) : '';
        var preferred = hint ? unique(floors, function (row) { return floorNumber(row.floorName) === hint; }) : -1;
        var order = floors.map(function (_, i) { return i; });
        if (preferred >= 0) order = [preferred].concat(order.filter(function (i) { return i !== preferred; }));
        var chosenFloor = -1, chosenRoom = -1, rooms;
        for (var n = 0; n < order.length; n++) {
            rooms = await select('floorChange', order[n], 'roomList');
            chosenRoom = unique(rooms, function (row) { return roomNumber(row.roomName) === plan.room; });
            if (chosenRoom >= 0) { chosenFloor = order[n]; break; }
        }
        if (chosenFloor < 0) throw new Error('room');
        await select('roomChange', chosenRoom);
        var selected = [systems[system].elcname, areas[area].areaName, districts[district].districtName,
            buildings[building].buiName, floors[chosenFloor].floorName, rooms[chosenRoom].roomName];
        state.room = {buildingId: String(buildings[building].buiId), roomId: String(rooms[chosenRoom].roomId),
            buildingNumber: plan.building, roomNumber: plan.room, selection: selected};
        state.status = 'matched';
        // Called separately after native has captured the expected IDs, before navigation.
        state.submit = function () {
            if (!active() || state.status !== 'matched') return 'cancelled';
            if (page.sysIndex !== system || page.areaIndex !== area || page.distIndex !== district
                || page.buildIndex !== building || page.floorIndex !== chosenFloor || page.roomIndex !== chosenRoom
                || String(page.buildList[building].buiId) !== state.room.buildingId
                || String(page.roomList[chosenRoom].roomId) !== state.room.roomId) return 'changed';
            state.status = 'submitted';
            page.query(); // School read-only query and its own room-binding confirmation.
            return 'submitted';
        };
    }
    match().catch(function (error) { fail(error.message || 'page'); });
    return JSON.stringify(state);
})();
