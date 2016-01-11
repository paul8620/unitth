function init(){
   updateViewport();
   window.onresize = updateViewport;
}

function updateViewport(){
   var viewportheight;

   // the more standards compliant browsers (mozilla/netscape/opera/IE7) use window.innerWidth and window.innerHeight

   if (typeof window.innerWidth != 'undefined')
   {
      viewportheight = window.innerHeight
   }

   // IE6 in standards compliant mode (i.e. with a valid doctype as the first line in the document)

   else if (typeof document.documentElement != 'undefined'
      && typeof document.documentElement.clientWidth !=
      'undefined' && document.documentElement.clientWidth != 0)
   {
      viewportheight = document.documentElement.clientHeight
   }
   // older versions of IE
   else
   {
      viewportheight = document.getElementsByTagName('body')[0].clientHeight
   }

   iframe = document.getElementById("myframe");
   iframe.style.height = (viewportheight-30)+'px';
}
