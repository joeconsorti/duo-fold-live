package org.duofold.live

import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable internal fun DuoSignature(){
 Row(Modifier.fillMaxWidth().padding(bottom=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
  Canvas(Modifier.size(44.dp)){
   val w=size.width;val h=size.height
   drawRoundRect(Color(0xffb4c8ff),Offset(w*.05f,h*.12f),Size(w*.4f,h*.76f),CornerRadius(w*.08f),style=Stroke(2.dp.toPx()))
   drawRoundRect(Brush.linearGradient(listOf(Color(0xffa7dfd1),Color(0xffb4c8ff))),Offset(w*.55f,h*.12f),Size(w*.4f,h*.76f),CornerRadius(w*.08f))
  }
  Text("MADE FOR THE FOLD",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.secondary)
 }
}
@Composable internal fun WallpaperInvitation(onClick:()->Unit){
 Row(Modifier.fillMaxWidth().heightIn(min=112.dp).clip(RoundedCornerShape(20.dp)).clickable(role=Role.Button,onClick=onClick).background(Brush.linearGradient(listOf(Color(0xff253c52),Color(0xff302b49))),RoundedCornerShape(20.dp)).padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(20.dp)){
  Canvas(Modifier.width(100.dp).height(80.dp)){
   val h=size.height;val w=size.width
   drawRoundRect(Brush.linearGradient(listOf(Color(0xffb8d9ed),Color(0xff718cc1))),Offset.Zero,Size(w*.34f,h),CornerRadius(8.dp.toPx()))
   drawRoundRect(Brush.linearGradient(listOf(Color(0xffafc8bc),Color(0xff7d76b1))),Offset(w*.43f,h*.16f),Size(w*.57f,h*.68f),CornerRadius(8.dp.toPx()))
  }
  Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){
   Text("Choose custom wallpaper",style=MaterialTheme.typography.titleMedium)
   Text("Your photos. Each screen. Your way. →",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
  }
 }
}
