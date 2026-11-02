/* Read only: return a fixed signal, never page text, credentials or payment data. */
(function () {
    if (location.origin !== 'https://ecard.sit.edu.cn' ||
        !/^\/(?:epay\/h5|epeortal\/pages\/h5)(?:\/|$)/.test(location.pathname)) return 'unsupported';
    var nodes = document.querySelectorAll('.weui_dialog_bd,.weui-dialog__bd,.uni-modal__bd,[role="alert"],[role="dialog"]');
    for (var i = 0; i < nodes.length; i++) {
        if (!nodes[i].getClientRects().length) continue;
        var text = (nodes[i].innerText || '').replace(/\s+/g, '').replace(/[，,。.!！:：]/g, '');
        if (text === '登录已过期请重新打开页面' || text === '错误登录已过期请重新打开页面') return 'expired';
    }
    return 'ok';
})()
