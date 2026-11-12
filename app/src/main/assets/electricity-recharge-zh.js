(function () {
    function onElectricityPage() {
        return location.origin === 'https://ecard.sit.edu.cn'
            && /^\/epeortal\/pages\/h5\/elec(?:Charge|Query)\/?$/.test(location.pathname);
    }
    if (!onElectricityPage() || window.__xinxinRechargeChinese) return;

    const labels = new Map([
        ['Room name', '寝室'],
        ['Electric Balance', '电费余额（元）'],
        ['电费余额', '电费余额（元）'],
        ['Recharge amount', '充值金额'],
        ['Please enter the amount', '请输入金额'],
        ['Wrong room number? Click here to re-select', '寝室号不对？点击重新选择'],
        ['Recharge', '充值'],
        ['Please select electric system', '请选择电控系统'],
        ['Please select Campus', '请选择校区'],
        ['Please select a region', '请选择区域'],
        ['Please select the building', '请选择楼栋'],
        ['Please select floor', '请选择楼层'],
        ['Please select a room', '请选择房间'],
        ['Query', '查询'],
        ['Welcome to the electronic control system!', '欢迎访问电控系统！']
    ]);
    function translate(value) {
        const text = value.trim().replace(/\s+/g, ' ');
        return labels.get(text)
            || (/^[•·]?\s*[“"']Electric Balance[”"']\s+here is not real-time query,\s*updated once a day,\s*the actual electricity is subject to the meter display\.?$/i.test(text)
                ? '• 电费余额每天更新一次。' : null);
    }
    function update() {
        if (!onElectricityPage() || !document.body) return;
        const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
        for (let node = walker.nextNode(); node; node = walker.nextNode()) {
            if (node.parentElement.closest('script, style, textarea, [contenteditable]')) continue;
            const translated = translate(node.data);
            if (translated) node.data = translated;
        }
        for (const input of document.querySelectorAll('input[placeholder]')) {
            const translated = translate(input.placeholder);
            if (translated) input.placeholder = translated;
        }
    }
    const observer = new MutationObserver(update);
    observer.observe(document.documentElement, {
        subtree: true, childList: true, characterData: true,
        attributes: true, attributeFilter: ['placeholder']
    });
    window.__xinxinRechargeChinese = update;
    update();
})();
