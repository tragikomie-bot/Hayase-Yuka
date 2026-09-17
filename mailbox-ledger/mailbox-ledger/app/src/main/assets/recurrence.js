'use strict';
(function(root){
function iso(d){return d.getFullYear()+'-'+String(d.getMonth()+1).padStart(2,'0')+'-'+String(d.getDate()).padStart(2,'0');}
function first(frequency,day,start){const d=new Date(start+'T12:00:00');if(!Number.isInteger(day)||isNaN(d))throw Error('周期或日期无效');if(frequency==='weekly'){if(day<1||day>7)throw Error('星期无效');d.setDate(d.getDate()+(day-((d.getDay()+6)%7+1)+7)%7);return iso(d);}if(frequency!=='monthly'||day<1||day>31)throw Error('月份日期无效');let y=d.getFullYear(),m=d.getMonth();let x=new Date(y,m,Math.min(day,new Date(y,m+1,0).getDate()),12);if(x<d)x=new Date(y,m+1,Math.min(day,new Date(y,m+2,0).getDate()),12);return iso(x);}
function after(frequency,day,previous){const d=new Date(previous+'T12:00:00');d.setDate(d.getDate()+1);return first(frequency,day,iso(d));}
root.Recurrence={first,after};if(typeof module!=='undefined')module.exports=root.Recurrence;
})(typeof window!=='undefined'?window:globalThis);
