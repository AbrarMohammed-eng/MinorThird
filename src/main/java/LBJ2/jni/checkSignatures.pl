#!/usr/bin/perl
# LBJ2/jni/checkSignatures.pl.  Generated from checkSignatures.pl.in by configure.

die "usage: checkSignatures.pl <header files>\n" unless @ARGV > 0;

for ($i = 0; $i < @ARGV; ++$i)
{
  $headerFile = $ARGV[$i];
  ($cFile) = ($headerFile =~ /^(.*).$/);
  $cFile .= "c";
  die "$headerFile or $cFile is missing.\n"
    if (!-e $headerFile || !-e $cFile);

  open IN, $cFile or die "Can't open $cFile for input: $!\n";
  while (<IN>)
  {
    $signature = $1 if /\*\s+Method:\s+(\S+)/;
    $signatures{$signature . $1} = 1 if /\*\s+Signature:\s+(\S+)/;
  }
  close IN;

  open IN, $headerFile or die "Can't open $headerFile for input: $!\n";
  while (<IN>)
  {
    $signature = $1 if /\*\s+Method:\s+(\S+)/;

    if (/\*\s+Signature:\s+(\S+)/)
    {
      $signature .= $1 ;
      die "Signature $signature exists in $headerFile but not in $cFile.\n"
        if !exists $signatures{$signature};
      ++$signatures{$signature};
    }
  }
  close IN;

  for (keys %signatures)
  {
    die "Signature $_ exists in $cFile but not in $headerFile.\n"
      if $signatures{$_} == 1;
  }
}

